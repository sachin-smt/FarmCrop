import { createContext, useContext, useState, type ReactNode } from 'react';
import type { UserProfile } from '../types/auth';

export interface AuthUser { id: string; email: string | null; }
export interface AuthSession { user: AuthUser; }
export interface PasswordAuthRequest { usernameOrEmail: string; password: string; }
export type OAuthProvider = 'google' | 'facebook' | 'x' | 'apple';

interface AuthContextValue {
  user: AuthUser | null;
  session: AuthSession | null;
  profile: UserProfile | null;
  isLoading: boolean;
  error: string | null;
  isAuthenticated: boolean;
  needsPhoneVerification: boolean;
  signInWithPassword: (credentials: PasswordAuthRequest) => Promise<void>;
  signInWithOAuth: (provider: OAuthProvider) => Promise<void>;
  sendOtp: (phone: string) => Promise<void>;
  verifyOtp: (phone: string, token: string) => Promise<void>;
  logout: () => Promise<void>;
  updatePreferredLanguage: (language: string) => Promise<void>;
  refreshProfile: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);
const backendUnavailableMessage = 'Authentication is awaiting backend integration.';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [error, setError] = useState<string | null>(null);

  const unavailable = async (): Promise<never> => {
    setError(backendUnavailableMessage);
    throw new Error(backendUnavailableMessage);
  };

  const signInWithPassword = async (_credentials: PasswordAuthRequest) => unavailable();
  const signInWithOAuth = async (_provider: OAuthProvider) => unavailable();

  const sendOtp = async (_phone: string) => unavailable();
  const verifyOtp = async (_phone: string, _token: string) => unavailable();
  const updatePreferredLanguage = async () => setError(backendUnavailableMessage);
  const refreshProfile = async () => undefined;
  const logout = async () => setError(null);

  const value: AuthContextValue = {
    user: null,
    session: null,
    profile: null,
    isLoading: false,
    error,
    isAuthenticated: false,
    needsPhoneVerification: false,
    signInWithPassword,
    signInWithOAuth,
    sendOtp,
    verifyOtp,
    logout,
    updatePreferredLanguage,
    refreshProfile,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside AuthProvider');
  return value;
}
