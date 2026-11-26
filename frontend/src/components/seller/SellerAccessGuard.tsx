import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';

export function SellerAccessGuard() {
  const { isLoading, isAuthenticated, needsPhoneVerification, profile } = useAuth();
  const location = useLocation();

  if (isLoading) return <main className="container grid min-h-[50vh] place-items-center text-sm text-[#66736b]">Loading your Seller access...</main>;
  if (!isAuthenticated) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (needsPhoneVerification) return <Navigate to="/verify-phone" replace state={{ from: location.pathname }} />;
  if (profile?.sellerStatus !== 'approved') return <Navigate to="/seller/application-status" replace state={{ from: location.pathname }} />;

  return <Outlet />;
}
