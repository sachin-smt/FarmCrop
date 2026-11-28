import { describe, expect, it } from 'vitest';
import { FirebaseSignInError, getGoogleIdToken, isFirebaseConfigured, signInErrorKey, startPhoneVerification } from './firebaseAuth';

describe('firebaseAuth', () => {
  it.skipIf(isFirebaseConfigured())('is disabled without Firebase web config and never pretends to sign in', async () => {
    // Unit tests run without VITE_FIREBASE_* values.
    expect(isFirebaseConfigured()).toBe(false);
    await expect(getGoogleIdToken('en')).rejects.toMatchObject({ code: 'farmcrop/not-configured', cancelled: false });
    await expect(startPhoneVerification('+919876543210', 'en')).rejects.toBeInstanceOf(FirebaseSignInError);
  });

  it('maps Firebase error codes to user-facing messages', () => {
    expect(signInErrorKey('auth/invalid-verification-code')).toBe('auth.invalidCode');
    expect(signInErrorKey('auth/code-expired')).toBe('auth.codeExpired');
    expect(signInErrorKey('auth/too-many-requests')).toBe('auth.tooManyRequests');
    expect(signInErrorKey('auth/billing-not-enabled')).toBe('auth.smsNotEnabled');
    expect(signInErrorKey('auth/operation-not-allowed')).toBe('auth.firebaseOperationNotAllowed');
    expect(signInErrorKey('auth/invalid-api-key')).toBe('auth.firebaseConfigurationError');
    expect(signInErrorKey('auth/invalid-app-credential')).toBe('auth.captchaFailed');
    expect(signInErrorKey('auth/popup-blocked')).toBe('auth.popupBlocked');
    expect(signInErrorKey('something-new')).toBe('auth.signInFailed');
  });
});
