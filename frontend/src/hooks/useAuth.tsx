import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import type { UserProfile } from '../types/auth';
import { ApiError, setAccessTokenProvider, setUnauthorizedHandler } from '../services/apiClient';
import * as authService from '../services/authService';
import type { AuthResponse, PendingRegistration, RegisterInput, VerifyRegistrationInput } from '../services/authService';
import { FirebaseSignInError, getGoogleIdToken, isFirebaseConfigured, signInErrorKey, startPhoneVerification, type PhoneChallenge } from '../services/firebaseAuth';
import i18n from '../i18n';

export interface AuthUser { id: string; email: string | null; }
export interface AuthSession { user: AuthUser; }
export interface PasswordAuthRequest { usernameOrEmail: string; password: string; }
export type OAuthProvider = 'google' | 'facebook' | 'x' | 'apple';
/** Sign-in methods that are configured on both the frontend (Firebase web config) and the backend. */
export interface AvailableMethods { google: boolean; phone: boolean; }
/** The SMS code currently awaited (for the resend cooldown). */
export interface PendingOtp { phone: string; sentAt: number; }
/** Seconds a user must wait before requesting another SMS code. */
export const OTP_RESEND_COOLDOWN_SECONDS = 30;

interface AuthContextValue {
  user: AuthUser | null;
  session: AuthSession | null;
  profile: UserProfile | null;
  isLoading: boolean;
  error: string | null;
  isAuthenticated: boolean;
  needsPhoneVerification: boolean;
  signInWithPassword: (credentials: PasswordAuthRequest) => Promise<UserProfile>;
  /** Step 1 of email registration: sends a code. No session is created. */
  register: (input: RegisterInput) => Promise<PendingRegistration>;
  /** Step 2: verifies the emailed code and signs the new account in. */
  verifyRegistration: (input: VerifyRegistrationInput) => Promise<UserProfile>;
  resendRegistrationCode: (email: string) => Promise<PendingRegistration>;
  /** Resolves to the signed-in profile, or null if the user closed the Google window. */
  signInWithOAuth: (provider: OAuthProvider) => Promise<UserProfile | null>;
  sendOtp: (phone: string) => Promise<void>;
  verifyOtp: (phone: string, code: string) => Promise<UserProfile>;
  /** Signed-in user: confirms the SMS code and marks the number verified on their own account. */
  verifyPhoneForAccount: (phone: string, code: string) => Promise<UserProfile>;
  methods: AvailableMethods;
  pendingOtp: PendingOtp | null;
  logout: () => Promise<void>;
  updatePreferredLanguage: (language: string) => Promise<void>;
  refreshProfile: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * Session handling:
 * - the access token lives only in memory (this ref), never in localStorage/sessionStorage;
 * - the refresh token is an HttpOnly cookie the JavaScript cannot read;
 * - on page load the session is restored by calling /auth/refresh (only if this browser has signed in before).
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const accessToken = useRef<string | null>(null);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [methods, setMethods] = useState<AvailableMethods>({ google: false, phone: false });
  const [pendingOtp, setPendingOtp] = useState<PendingOtp | null>(null);
  const phoneChallenge = useRef<PhoneChallenge | null>(null);

  // Offer Google/phone only when the browser has Firebase config AND the backend can verify the tokens.
  useEffect(() => {
    if (!isFirebaseConfigured()) return;
    let cancelled = false;
    authService.getAuthProviders()
      .then((providers) => { if (!cancelled) setMethods({ google: providers.google, phone: providers.phone }); })
      .catch(() => { /* backend unreachable: keep them disabled rather than offer a button that cannot work */ });
    return () => { cancelled = true; };
  }, []);

  const applySession = useCallback((response: AuthResponse) => {
    accessToken.current = response.accessToken;
    authService.sessionHint.set();
    setProfile(authService.toUserProfile(response.user));
    setError(null);
    return authService.toUserProfile(response.user);
  }, []);

  const clearSession = useCallback(() => {
    accessToken.current = null;
    authService.sessionHint.clear();
    setProfile(null);
  }, []);

  useEffect(() => {
    setAccessTokenProvider(() => accessToken.current);
    setUnauthorizedHandler(async () => {
      try {
        applySession(await authService.refreshSession());
        return true;
      } catch {
        clearSession();
        return false;
      }
    });
    return () => {
      setAccessTokenProvider(() => null);
      setUnauthorizedHandler(null);
    };
  }, [applySession, clearSession]);

  useEffect(() => {
    let cancelled = false;
    if (!authService.sessionHint.isSet()) {
      setIsLoading(false);
      return () => { cancelled = true; };
    }
    authService.refreshSession()
      .then((response) => { if (!cancelled) applySession(response); })
      .catch((caught: unknown) => {
        if (cancelled) return;
        clearSession();
        // 401 simply means "not signed in". Anything else (server down) is worth surfacing.
        if (!(caught instanceof ApiError && caught.status === 401)) {
          setError(caught instanceof Error ? caught.message : 'Unable to restore your session.');
        }
      })
      .finally(() => { if (!cancelled) setIsLoading(false); });
    return () => { cancelled = true; };
  }, [applySession, clearSession]);

  const signInWithPassword = useCallback(async (credentials: PasswordAuthRequest) => {
    try {
      return applySession(await authService.login(credentials));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Sign-in failed.');
      throw caught;
    }
  }, [applySession]);

  const register = useCallback((input: RegisterInput) => authService.register(input), []);
  const verifyRegistration = useCallback(async (input: VerifyRegistrationInput) => applySession(await authService.verifyRegistration(input)), [applySession]);
  const resendRegistrationCode = useCallback((email: string) => authService.resendRegistrationCode(email), []);

  const logout = useCallback(async () => {
    try {
      await authService.logout();
    } finally {
      // Even if the server is unreachable, forget the in-memory session locally.
      clearSession();
      setError(null);
    }
  }, [clearSession]);

  const refreshProfile = useCallback(async () => {
    if (!accessToken.current) return;
    setProfile(authService.toUserProfile(await authService.getMe()));
  }, []);

  const updatePreferredLanguage = useCallback(async (language: string) => {
    if (!profile) return;
    const updated = await authService.updateMe({ fullName: profile.name ?? '', phone: profile.phoneNumber, preferredLanguage: language });
    setProfile(authService.toUserProfile(updated));
  }, [profile]);

  // Providers that are not implemented: fail visibly instead of faking a login.
  const unavailable = useCallback(async (messageKey: string): Promise<never> => {
    const message = i18n.t(messageKey);
    setError(message);
    throw new Error(message);
  }, []);

  /** Turns Firebase/API failures into a user-facing Error (and records it in `error`). */
  const fail = useCallback((caught: unknown): Error => {
    const message = caught instanceof FirebaseSignInError
      ? i18n.t(signInErrorKey(caught.code))
      : caught instanceof Error ? caught.message : i18n.t('auth.signInFailed');
    setError(message);
    return new Error(message);
  }, []);

  const signInWithOAuth = useCallback(async (provider: OAuthProvider): Promise<UserProfile | null> => {
    if (provider !== 'google' || !methods.google) return unavailable('auth.socialUnavailable');
    setError(null);
    let idToken: string;
    try {
      idToken = await getGoogleIdToken(i18n.language);
    } catch (caught) {
      if (caught instanceof FirebaseSignInError && caught.cancelled) return null;
      throw fail(caught);
    }
    try {
      return applySession(await authService.loginWithGoogle(idToken));
    } catch (caught) {
      throw fail(caught);
    }
  }, [methods.google, unavailable, fail, applySession]);

  const sendOtp = useCallback(async (phone: string) => {
    if (!methods.phone) return unavailable('auth.otpUnavailable');
    if (!/^\+91[6-9]\d{9}$/.test(phone)) throw fail(new FirebaseSignInError('auth/invalid-phone-number'));
    if (pendingOtp?.phone === phone && Date.now() - pendingOtp.sentAt < OTP_RESEND_COOLDOWN_SECONDS * 1000) {
      throw fail(new FirebaseSignInError('auth/too-many-requests'));
    }
    setError(null);
    try {
      phoneChallenge.current = await startPhoneVerification(phone, i18n.language);
      setPendingOtp({ phone, sentAt: Date.now() });
    } catch (caught) {
      throw fail(caught);
    }
  }, [methods.phone, pendingOtp, unavailable, fail]);

  /** Confirms the pending SMS code and returns a fresh Firebase ID token for the number. */
  const confirmPendingCode = useCallback(async (phone: string, code: string): Promise<string> => {
    const challenge = phoneChallenge.current;
    if (!challenge || challenge.phone !== phone) throw fail(new FirebaseSignInError('auth/code-expired'));
    setError(null);
    let idToken: string;
    try {
      idToken = await challenge.confirm(code);
    } catch (caught) {
      throw fail(caught);
    }
    // The code is used up whatever the backend says next; a retry needs a new SMS.
    phoneChallenge.current = null;
    setPendingOtp(null);
    return idToken;
  }, [fail]);

  const verifyOtp = useCallback(async (phone: string, code: string): Promise<UserProfile> => {
    const idToken = await confirmPendingCode(phone, code);
    try {
      return applySession(await authService.loginWithPhone(idToken));
    } catch (caught) {
      throw fail(caught);
    }
  }, [confirmPendingCode, fail, applySession]);

  const verifyPhoneForAccount = useCallback(async (phone: string, code: string): Promise<UserProfile> => {
    if (!accessToken.current) throw fail(new Error(i18n.t('auth.phoneRequired')));
    const idToken = await confirmPendingCode(phone, code);
    try {
      const updated = authService.toUserProfile(await authService.verifyMyPhone(idToken));
      setProfile(updated);
      return updated;
    } catch (caught) {
      throw fail(caught);
    }
  }, [confirmPendingCode, fail]);

  const value = useMemo<AuthContextValue>(() => {
    const user = profile ? { id: profile.id, email: profile.email } : null;
    return {
      user,
      session: user ? { user } : null,
      profile,
      isLoading,
      error,
      isAuthenticated: profile !== null,
      needsPhoneVerification: false,
      signInWithPassword,
      register,
      verifyRegistration,
      resendRegistrationCode,
      signInWithOAuth,
      sendOtp,
      verifyOtp,
      verifyPhoneForAccount,
      methods,
      pendingOtp,
      logout,
      updatePreferredLanguage,
      refreshProfile,
    };
  }, [profile, isLoading, error, signInWithPassword, register, verifyRegistration, resendRegistrationCode, signInWithOAuth, sendOtp, verifyOtp, verifyPhoneForAccount, methods, pendingOtp, logout, updatePreferredLanguage, refreshProfile]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside AuthProvider');
  return value;
}

/** Where a user lands after signing in when no specific page was requested. */
export function defaultLandingPath(profile: UserProfile): string {
  if (profile.role === 'admin') return '/admin/dashboard';
  if (profile.role === 'farmer') return '/seller/dashboard';
  return '/marketplace';
}
