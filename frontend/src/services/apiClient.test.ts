import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api, buildUrl, setAccessTokenProvider, setUnauthorizedHandler } from './apiClient';

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('apiClient', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
    setAccessTokenProvider(() => null);
    setUnauthorizedHandler(null);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('prefixes paths with /api/v1 and drops empty query values', () => {
    expect(buildUrl('/products', { q: 'wheat', page: 0, category: '', city: undefined }))
      .toBe('/api/v1/products?q=wheat&page=0');
    expect(buildUrl('/api/v1/cart')).toBe('/api/v1/cart');
  });

  it('unwraps the data field of a successful envelope', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { success: true, data: { status: 'UP' } }));

    await expect(api.get<{ status: string }>('/public/status')).resolves.toEqual({ status: 'UP' });
    const [, init] = fetchMock.mock.calls[0];
    expect(init?.credentials).toBe('include');
  });

  it('sends JSON bodies and the bearer token when available', async () => {
    setAccessTokenProvider(() => 'token-123');
    fetchMock.mockResolvedValue(jsonResponse(201, { success: true, data: { id: 1 } }));

    await api.post('/cart/items', { productId: 7, quantity: 2 });

    const [, init] = fetchMock.mock.calls[0];
    const headers = new Headers(init?.headers);
    expect(headers.get('Authorization')).toBe('Bearer token-123');
    expect(headers.get('Content-Type')).toBe('application/json');
    expect(init?.body).toBe(JSON.stringify({ productId: 7, quantity: 2 }));
  });

  it('does not attach a token to requests marked auth: false', async () => {
    setAccessTokenProvider(() => 'token-123');
    fetchMock.mockResolvedValue(jsonResponse(200, { success: true, data: null }));

    await api.get('/public/status', { auth: false });

    expect(new Headers(fetchMock.mock.calls[0][1]?.headers).has('Authorization')).toBe(false);
  });

  it('leaves multipart Content-Type to the browser for FormData', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { success: true, data: null }));
    const form = new FormData();
    form.append('file', new Blob(['x']), 'doc.pdf');

    await api.post('/seller/documents', form);

    const [, init] = fetchMock.mock.calls[0];
    expect(new Headers(init?.headers).has('Content-Type')).toBe(false);
    expect(init?.body).toBe(form);
  });

  it('raises ApiError with code and field errors from an error envelope', async () => {
    fetchMock.mockResolvedValue(jsonResponse(400, {
      success: false,
      code: 'VALIDATION_FAILED',
      message: 'Please correct the highlighted fields.',
      errors: [{ field: 'email', message: 'Email is already registered.' }],
    }));

    const error = await api.post('/auth/register', {}).catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(400);
    expect(apiError.code).toBe('VALIDATION_FAILED');
    expect(apiError.fieldErrorMap()).toEqual({ email: 'Email is already registered.' });
  });

  it('reports non-JSON error responses without inventing data', async () => {
    fetchMock.mockResolvedValue(new Response('<html>Bad gateway</html>', { status: 502, headers: { 'Content-Type': 'text/html' } }));

    await expect(api.get('/products')).rejects.toMatchObject({ status: 502, code: 'HTTP_502' });
  });

  it('maps network failures to a NETWORK_ERROR ApiError', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

    await expect(api.get('/products')).rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' });
  });

  it('retries once after a successful session refresh on 401', async () => {
    let token = 'expired';
    setAccessTokenProvider(() => token);
    setUnauthorizedHandler(async () => { token = 'fresh'; return true; });
    fetchMock
      .mockResolvedValueOnce(jsonResponse(401, { success: false, code: 'UNAUTHORIZED', message: 'Authentication is required.' }))
      .mockResolvedValueOnce(jsonResponse(200, { success: true, data: { id: 5 } }));

    await expect(api.get('/users/me')).resolves.toEqual({ id: 5 });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(new Headers(fetchMock.mock.calls[1][1]?.headers).get('Authorization')).toBe('Bearer fresh');
  });

  it('surfaces 401 when the refresh fails', async () => {
    setUnauthorizedHandler(async () => false);
    fetchMock.mockResolvedValue(jsonResponse(401, { success: false, code: 'UNAUTHORIZED', message: 'Authentication is required.' }));

    await expect(api.get('/users/me')).rejects.toMatchObject({ status: 401, code: 'UNAUTHORIZED' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
