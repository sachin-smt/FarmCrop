import { ApiError, api } from './apiClient';
import type { UserProfile, UserRole } from '../types/auth';

/** Backend roles (com.farmcrop.common.enums.UserRole). */
export type BackendRole = 'BUYER' | 'SELLER' | 'ADMIN';

/** Mirrors backend UserProfileResponse. */
export interface BackendUser {
  id: number;
  fullName: string;
  username: string;
  /** Null for accounts created by phone sign-in. */
  email: string | null;
  phone: string | null;
  emailVerified: boolean;
  phoneVerified: boolean;
  roles: BackendRole[];
  accountStatus: 'ACTIVE' | 'SUSPENDED' | 'DISABLED';
  preferredLanguage: string;
  createdAt: string;
}

/** Mirrors backend AuthResponse. The refresh token is never in the body (HttpOnly cookie). */
export interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  accessTokenExpiresAt: string;
  user: BackendUser;
}

export interface RegisterInput {
  fullName: string;
  username: string;
  email: string;
  phone: string;
  password: string;
  confirmPassword: string;
}

/** Mirrors backend PendingRegistrationResponse: a code was emailed; no session exists yet. */
export interface PendingRegistration {
  email: string;
  verificationRequired: true;
  expiresAt: string;
  resendAvailableAt: string;
}

/** Completes registration. The password must be the one chosen at registration. */
export interface VerifyRegistrationInput {
  email: string;
  otp: string;
  password: string;
  /** Optional replacement username (e.g. the chosen one was taken before the account was created). */
  username?: string;
}

export interface LoginInput {
  usernameOrEmail: string;
  password: string;
}

export interface UpdateProfileInput {
  fullName: string;
  phone: string | null;
  preferredLanguage?: string;
}

/** Required by the backend on cookie-authenticated endpoints (refresh/logout) as CSRF defence. */
const CSRF_HEADERS = { 'X-Requested-With': 'XMLHttpRequest' };

/** Step 1: emails a 6-digit code. Returns no tokens. */
export function register(input: RegisterInput) {
  return api.post<PendingRegistration>('/auth/register', input, { auth: false });
}

/** Step 2: verifies the emailed code and starts the session (access token + HttpOnly refresh cookie). */
export function verifyRegistration(input: VerifyRegistrationInput) {
  return api.post<AuthResponse>('/auth/register/verify', input, { auth: false });
}

/** Sends a new code; the previous one stops working. */
export function resendRegistrationCode(email: string) {
  return api.post<PendingRegistration>('/auth/register/resend', { email }, { auth: false });
}

export function login(input: LoginInput) {
  return api.post<AuthResponse>('/auth/login', input, { auth: false });
}

/** Google sign-in/sign-up: exchanges a fresh Firebase ID token (verified by the backend) for a FarmCrop session. */
export function loginWithGoogle(idToken: string) {
  return api.post<AuthResponse>('/auth/google', { idToken }, { auth: false });
}

/** Mobile OTP sign-in/sign-up: exchanges a fresh Firebase phone-auth ID token for a FarmCrop session. */
export function loginWithPhone(idToken: string) {
  return api.post<AuthResponse>('/auth/phone', { idToken }, { auth: false });
}

/** Signed-in user proves ownership of a mobile number (fresh Firebase phone token); enables OTP login for the account. */
export function verifyMyPhone(idToken: string) {
  return api.post<BackendUser>('/users/me/phone/verify', { idToken });
}

/** Which sign-in methods the backend can verify (mirrors AuthProvidersController). */
export interface AuthProviders { password: boolean; google: boolean; phone: boolean; }

export function getAuthProviders() {
  return api.get<AuthProviders>('/public/auth-providers', { auth: false });
}

export function logout() {
  return api.post<null>('/auth/logout', undefined, { auth: false, headers: CSRF_HEADERS });
}

export function getMe() {
  return api.get<BackendUser>('/users/me');
}

export function updateMe(input: UpdateProfileInput) {
  return api.put<BackendUser>('/users/me', input);
}

/**
 * Non-sensitive marker ("this browser has signed in"). It holds no token or personal data; it only
 * lets anonymous visitors skip a pointless /auth/refresh round trip (and a 401) on every page load.
 */
const SESSION_HINT_KEY = 'farmcrop.hasSession';

export const sessionHint = {
  isSet: () => {
    try { return window.localStorage.getItem(SESSION_HINT_KEY) === '1'; } catch { return false; }
  },
  set: () => {
    try { window.localStorage.setItem(SESSION_HINT_KEY, '1'); } catch { /* storage disabled: session restore is best-effort */ }
  },
  clear: () => {
    try { window.localStorage.removeItem(SESSION_HINT_KEY); } catch { /* ignore */ }
  },
};

let refreshInFlight: Promise<AuthResponse> | null = null;

async function refreshOnce(): Promise<AuthResponse> {
  try {
    return await api.post<AuthResponse>('/auth/refresh', undefined, { auth: false, headers: CSRF_HEADERS });
  } catch (error) {
    // Another tab rotated the shared cookie a moment ago; the browser now holds the new one.
    if (error instanceof ApiError && error.code === 'REFRESH_RACE') {
      return api.post<AuthResponse>('/auth/refresh', undefined, { auth: false, headers: CSRF_HEADERS });
    }
    throw error;
  }
}

/**
 * Exchanges the HttpOnly refresh cookie for a new access token. Concurrent callers share one
 * request, so React StrictMode double effects or parallel 401s never present the same token twice.
 */
export function refreshSession(): Promise<AuthResponse> {
  if (!refreshInFlight) {
    refreshInFlight = refreshOnce().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

/** Maps the backend user onto the profile shape the existing UI components already use. */
export function toUserProfile(user: BackendUser): UserProfile {
  const role: UserRole = user.roles.includes('ADMIN') ? 'admin' : user.roles.includes('SELLER') ? 'farmer' : 'customer';
  return {
    id: String(user.id),
    email: user.email,
    name: user.fullName,
    username: user.username,
    avatar: null,
    phoneNumber: user.phone,
    phoneVerified: user.phoneVerified,
    authProvider: null,
    role,
    // Seller application status is provided by the seller module (milestone 3); until then only
    // an approved seller role is known.
    sellerStatus: user.roles.includes('SELLER') ? 'approved' : 'buyer',
    preferredLanguage: user.preferredLanguage,
    createdAt: user.createdAt,
    updatedAt: null,
  };
}
