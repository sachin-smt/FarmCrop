/**
 * Firebase Authentication is used only to *prove* a Google account or a phone number (SMS OTP).
 * The resulting Firebase ID token is sent once to the FarmCrop backend, which verifies it and starts the
 * normal FarmCrop session (in-memory access token + HttpOnly refresh cookie). The Firebase session itself
 * is kept in memory only and signed out immediately, so no Firebase credentials persist in the browser.
 *
 * Config values are public web-app identifiers (not secrets); they come from VITE_FIREBASE_* env vars.
 * The SDK is loaded lazily so pages that never sign in do not download it.
 */
import type { Auth, ConfirmationResult, RecaptchaVerifier } from 'firebase/auth';

const config = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY as string | undefined,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN as string | undefined,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID as string | undefined,
  appId: import.meta.env.VITE_FIREBASE_APP_ID as string | undefined,
};

export function isFirebaseConfigured(): boolean {
  return Boolean(config.apiKey && config.authDomain && config.projectId && config.appId);
}

/** A sign-in step failed. `code` is the Firebase error code (or our own); `cancelled` means the user backed out. */
export class FirebaseSignInError extends Error {
  readonly code: string;
  readonly cancelled: boolean;
  constructor(code: string, cancelled = false) {
    super(code);
    this.name = 'FirebaseSignInError';
    this.code = code;
    this.cancelled = cancelled;
  }
}

type AuthModule = typeof import('firebase/auth');
let loaded: Promise<{ auth: Auth; mod: AuthModule }> | null = null;

function load() {
  if (!isFirebaseConfigured()) return Promise.reject(new FirebaseSignInError('farmcrop/not-configured'));
  if (!loaded) {
    loaded = Promise.all([import('firebase/app'), import('firebase/auth')]).then(([appModule, mod]) => {
      const app = appModule.getApps().length ? appModule.getApp() : appModule.initializeApp({
        apiKey: config.apiKey, authDomain: config.authDomain, projectId: config.projectId, appId: config.appId,
      });
      const auth = mod.initializeAuth(app, { persistence: mod.inMemoryPersistence, popupRedirectResolver: mod.browserPopupRedirectResolver });
      return { auth, mod };
    }).catch((error: unknown) => {
      loaded = null;
      throw error;
    });
  }
  return loaded;
}

const CANCELLED_CODES = new Set(['auth/popup-closed-by-user', 'auth/cancelled-popup-request', 'auth/user-cancelled']);

function toSignInError(error: unknown): FirebaseSignInError {
  if (error instanceof FirebaseSignInError) return error;
  const rawCode = typeof error === 'object' && error && 'code' in error ? String((error as { code: unknown }).code) : '';
  const code = /^[a-z0-9/-]{1,80}$/i.test(rawCode) ? rawCode : 'auth/internal-error';
  if (import.meta.env.DEV) {
    console.warn('[Firebase Auth] Request failed', {
      code,
      message: safeFirebaseErrorMessage(error),
    });
  }
  return new FirebaseSignInError(code, CANCELLED_CODES.has(code));
}

function safeFirebaseErrorMessage(error: unknown): string {
  const message = error instanceof Error ? error.message : 'Firebase Authentication request failed.';
  return message
    .replace(/https?:\/\/[^\s"'<>]+/gi, '[redacted URL]')
    .replace(/\bAIza[0-9A-Za-z_-]{20,}\b/g, '[redacted API key]')
    .replace(/\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b/g, '[redacted token]')
    .replace(/\+?\d[\d\s().-]{5,}\d/g, '[redacted number]')
    .slice(0, 240);
}

/** Exchanges the just-created Firebase session for its ID token, then drops the Firebase session. */
async function takeIdToken(auth: Auth, mod: AuthModule): Promise<string> {
  const user = auth.currentUser;
  if (!user) throw new FirebaseSignInError('auth/internal-error');
  try {
    return await user.getIdToken(true);
  } finally {
    await mod.signOut(auth).catch(() => undefined);
  }
}

/** Opens the Google account chooser and returns a fresh Firebase ID token for it. */
export async function getGoogleIdToken(languageCode: string): Promise<string> {
  try {
    const { auth, mod } = await load();
    auth.languageCode = languageCode;
    const provider = new mod.GoogleAuthProvider();
    provider.setCustomParameters({ prompt: 'select_account' });
    await mod.signInWithPopup(auth, provider);
    return await takeIdToken(auth, mod);
  } catch (error) {
    throw toSignInError(error);
  }
}

/** A pending SMS code for one phone number. `confirm` returns a fresh Firebase ID token. */
export interface PhoneChallenge {
  phone: string;
  confirm: (code: string) => Promise<string>;
}

let activeVerifier: RecaptchaVerifier | null = null;
let phoneVerificationInFlight = false;

function freshRecaptchaContainer(): HTMLElement {
  document.getElementById('farmcrop-recaptcha')?.remove();
  const container = document.createElement('div');
  container.id = 'farmcrop-recaptcha';
  document.body.appendChild(container);
  return container;
}

function clearActiveVerifier(): void {
  const verifier = activeVerifier;
  activeVerifier = null;
  verifier?.clear();
  document.getElementById('farmcrop-recaptcha')?.remove();
}

/**
 * Sends an SMS code to `phoneE164` (e.g. +919876543210). Firebase runs an invisible reCAPTCHA first, and
 * enforces its own per-number/per-device SMS quotas. During development use Firebase test phone numbers.
 */
export async function startPhoneVerification(phoneE164: string, languageCode: string): Promise<PhoneChallenge> {
  if (phoneVerificationInFlight) throw new FirebaseSignInError('auth/too-many-requests');
  phoneVerificationInFlight = true;
  try {
    const { auth, mod } = await load();
    auth.languageCode = languageCode;
    clearActiveVerifier();
    activeVerifier = new mod.RecaptchaVerifier(auth, freshRecaptchaContainer(), { size: 'invisible' });
    const confirmation: ConfirmationResult = await mod.signInWithPhoneNumber(auth, phoneE164, activeVerifier);
    return {
      phone: phoneE164,
      confirm: async (code: string) => {
        try {
          await confirmation.confirm(code);
          return await takeIdToken(auth, mod);
        } catch (error) {
          throw toSignInError(error);
        }
      },
    };
  } catch (error) {
    throw toSignInError(error);
  } finally {
    phoneVerificationInFlight = false;
    clearActiveVerifier();
  }
}

/** i18n key for a Firebase/sign-in error code. */
export function signInErrorKey(code: string): string {
  switch (code) {
    case 'farmcrop/not-configured':
    case 'auth/configuration-not-found':
    case 'auth/invalid-api-key':
    case 'auth/app-not-authorized':
      return 'auth.firebaseConfigurationError';
    case 'auth/operation-not-allowed':
      return 'auth.firebaseOperationNotAllowed';
    case 'auth/popup-blocked':
      return 'auth.popupBlocked';
    case 'auth/unauthorized-domain':
      return 'auth.unauthorizedDomain';
    case 'auth/invalid-phone-number':
    case 'auth/missing-phone-number':
      return 'auth.invalidPhone';
    case 'auth/invalid-verification-code':
    case 'auth/missing-verification-code':
      return 'auth.invalidCode';
    case 'auth/invalid-app-credential':
    case 'auth/missing-app-credential':
    case 'auth/captcha-check-failed':
      return 'auth.captchaFailed';
    case 'auth/code-expired':
    case 'auth/session-expired':
      return 'auth.codeExpired';
    case 'auth/too-many-requests':
    case 'auth/quota-exceeded':
      return 'auth.tooManyRequests';
    case 'auth/billing-not-enabled':
      return 'auth.smsNotEnabled';
    case 'auth/network-request-failed':
      return 'auth.networkError';
    default:
      return 'auth.signInFailed';
  }
}
