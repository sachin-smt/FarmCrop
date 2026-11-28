import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getAuthProviders, login, loginWithGoogle, loginWithPhone, logout, resendRegistrationCode, verifyMyPhone, verifyRegistration, refreshSession, register, toUserProfile, type AuthResponse, type BackendUser } from './authService';

const user: BackendUser = {
  id: 7,
  fullName: 'Asha Devi',
  username: 'asha',
  email: 'asha@example.test',
  phone: '9876543210',
  emailVerified: false,
  phoneVerified: false,
  roles: ['BUYER'],
  accountStatus: 'ACTIVE',
  preferredLanguage: 'hi',
  createdAt: '2026-11-26T10:00:00Z',
};

const authResponse: AuthResponse = { accessToken: 'jwt-1', tokenType: 'Bearer', accessTokenExpiresAt: '2026-11-26T10:15:00Z', user };

function ok(data: unknown, status = 200) {
  return new Response(JSON.stringify({ success: true, data }), { status, headers: { 'Content-Type': 'application/json' } });
}

function fail(status: number, code: string) {
  return new Response(JSON.stringify({ success: false, code, message: 'nope' }), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('authService', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => vi.unstubAllGlobals());

  it('posts login credentials without a bearer token and includes cookies', async () => {
    fetchMock.mockResolvedValue(ok(authResponse));

    await expect(login({ usernameOrEmail: 'asha', password: 'Harvest2026' })).resolves.toEqual(authResponse);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/auth/login');
    expect(init?.method).toBe('POST');
    expect(init?.credentials).toBe('include');
    expect(new Headers(init?.headers).has('Authorization')).toBe(false);
    expect(JSON.parse(String(init?.body))).toEqual({ usernameOrEmail: 'asha', password: 'Harvest2026' });
  });

  it('exchanges Google and phone ID tokens without a bearer token, sending only the token', async () => {
    fetchMock.mockImplementation(async () => ok(authResponse));
    await loginWithGoogle('google-id-token');
    await loginWithPhone('phone-id-token');

    const [googleUrl, googleInit] = fetchMock.mock.calls[0];
    const [phoneUrl, phoneInit] = fetchMock.mock.calls[1];
    expect(googleUrl).toBe('/api/v1/auth/google');
    expect(phoneUrl).toBe('/api/v1/auth/phone');
    expect(JSON.parse(String(googleInit?.body))).toEqual({ idToken: 'google-id-token' });
    expect(JSON.parse(String(phoneInit?.body))).toEqual({ idToken: 'phone-id-token' });
    expect(googleInit?.credentials).toBe('include');
    expect(new Headers(phoneInit?.headers).has('Authorization')).toBe(false);
  });

  it('verifies the signed-in user phone with only the Firebase token', async () => {
    fetchMock.mockResolvedValue(ok({ ...user, phoneVerified: true }));
    await expect(verifyMyPhone('phone-id-token')).resolves.toMatchObject({ phoneVerified: true });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/users/me/phone/verify');
    expect(init?.method).toBe('POST');
    expect(JSON.parse(String(init?.body))).toEqual({ idToken: 'phone-id-token' });
  });

  it('reads which sign-in methods the backend supports', async () => {
    fetchMock.mockResolvedValue(ok({ password: true, google: true, phone: false }));
    await expect(getAuthProviders()).resolves.toEqual({ password: true, google: true, phone: false });
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/public/auth-providers');
  });

  it('maps verified phone and email-less accounts from phone sign-in', () => {
    expect(toUserProfile({ ...user, email: null, phone: '+919876543210', phoneVerified: true }))
      .toMatchObject({ email: null, phoneNumber: '+919876543210', phoneVerified: true, role: 'customer' });
  });

  it('registers in two steps: the code request returns no tokens, verification starts the session', async () => {
    const pending = { email: 'asha@example.test', verificationRequired: true, expiresAt: '2026-11-28T10:10:00Z', resendAvailableAt: '2026-11-28T10:01:00Z' };
    fetchMock.mockResolvedValueOnce(ok(pending, 202)).mockResolvedValueOnce(ok(authResponse, 201)).mockResolvedValueOnce(ok(pending, 202));

    await expect(register({ fullName: 'Asha', username: 'asha', email: 'asha@example.test', phone: '9876543210', password: 'Harvest2026', confirmPassword: 'Harvest2026' }))
      .resolves.toEqual(pending);
    await expect(verifyRegistration({ email: 'asha@example.test', otp: '123456', password: 'Harvest2026' })).resolves.toEqual(authResponse);
    await resendRegistrationCode('asha@example.test');

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(['/api/v1/auth/register', '/api/v1/auth/register/verify', '/api/v1/auth/register/resend']);
    expect(JSON.parse(String(fetchMock.mock.calls[1][1]?.body))).toEqual({ email: 'asha@example.test', otp: '123456', password: 'Harvest2026' });
    expect(JSON.parse(String(fetchMock.mock.calls[2][1]?.body))).toEqual({ email: 'asha@example.test' });
    expect(fetchMock.mock.calls.every(([, init]) => init?.credentials === 'include' && !new Headers(init?.headers).has('Authorization'))).toBe(true);
  });

  it('never sends a role field when registering', async () => {
    fetchMock.mockResolvedValue(ok(authResponse, 201));
    await register({ fullName: 'Asha', username: 'asha', email: 'a@example.test', phone: '9876543210', password: 'Harvest2026', confirmPassword: 'Harvest2026' });
    const body = JSON.parse(String(fetchMock.mock.calls[0][1]?.body));
    expect(body).not.toHaveProperty('roles');
    expect(body).not.toHaveProperty('role');
  });

  it('sends the anti-CSRF header on refresh and logout', async () => {
    fetchMock.mockImplementation(async () => ok(authResponse));
    await refreshSession();
    await logout();
    for (const [, init] of fetchMock.mock.calls) {
      expect(new Headers(init?.headers).get('X-Requested-With')).toBe('XMLHttpRequest');
    }
  });

  it('shares one in-flight refresh between concurrent callers', async () => {
    let resolveFetch: (response: Response) => void = () => undefined;
    fetchMock.mockImplementation(() => new Promise<Response>((resolve) => { resolveFetch = resolve; }));

    const first = refreshSession();
    const second = refreshSession();
    resolveFetch(ok(authResponse));

    await expect(Promise.all([first, second])).resolves.toEqual([authResponse, authResponse]);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('retries once when another tab won a refresh race', async () => {
    fetchMock
      .mockResolvedValueOnce(fail(401, 'REFRESH_RACE'))
      .mockResolvedValueOnce(ok(authResponse));

    await expect(refreshSession()).resolves.toEqual(authResponse);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('does not retry when the session is genuinely expired', async () => {
    fetchMock.mockResolvedValue(fail(401, 'SESSION_EXPIRED'));

    await expect(refreshSession()).rejects.toMatchObject({ status: 401, code: 'SESSION_EXPIRED' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('maps backend roles to the existing UI roles', () => {
    expect(toUserProfile(user)).toMatchObject({ id: '7', name: 'Asha Devi', role: 'customer', sellerStatus: 'buyer', phoneVerified: false });
    expect(toUserProfile({ ...user, roles: ['BUYER', 'SELLER'] })).toMatchObject({ role: 'farmer', sellerStatus: 'approved' });
    expect(toUserProfile({ ...user, roles: ['ADMIN'] }).role).toBe('admin');
  });
});
