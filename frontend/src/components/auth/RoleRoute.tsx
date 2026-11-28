import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { ShieldAlert } from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import type { UserRole } from '../../types/auth';

/**
 * Client-side route guard for role-specific areas. This only controls what the UI shows; the
 * backend enforces the same rules on every API call.
 */
export function RoleRoute({ role }: { role: UserRole }) {
  const { isLoading, isAuthenticated, profile } = useAuth();
  const location = useLocation();

  if (isLoading) return <main className="container grid min-h-[50vh] place-items-center text-sm text-[#66736b]">Loading your session...</main>;
  if (!isAuthenticated) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (profile?.role !== role) {
    return <main className="container grid min-h-[60vh] place-content-center py-16 text-center">
      <ShieldAlert size={36} className="mx-auto text-[#a94837]" />
      <p className="mt-4 text-xs font-bold uppercase tracking-[.15em] text-[#0b6b3a]">403 · Access restricted</p>
      <h1 className="serif mt-3 text-3xl font-bold text-[#122018]">You don’t have access to this area.</h1>
      <a href="/" className="mt-5 text-sm font-bold text-[#0b6b3a] underline">Return to FarmCrop</a>
    </main>;
  }
  return <Outlet />;
}
