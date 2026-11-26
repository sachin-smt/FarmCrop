import { useState } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { Navbar } from './components/layout/Navbar';
import { Footer } from './components/layout/Footer';
import { CartDrawer } from './components/cart/CartDrawer';
import { ProtectedRoute } from './components/auth/ProtectedRoute';
import { Home } from './pages/Home';
import { Marketplace } from './pages/Marketplace';
import { Farmers } from './pages/Farmers';
import { Login } from './pages/Login';
import { VerifyPhone } from './pages/VerifyPhone';
import { Profile } from './pages/Profile';
import { PortalShell } from './features/portals/components/PortalShell';
import type { CartItem } from './types';
import { AdminDashboard } from './pages/admin/AdminDashboard';
import { SellerApplications } from './pages/admin/SellerApplications';
import { ApplicationDetails } from './pages/admin/ApplicationDetails';
import { ManageSellers } from './pages/admin/ManageSellers';
import { ManageBuyers } from './pages/admin/ManageBuyers';
import { ManageCrops } from './pages/admin/ManageCrops';
import { ManageOrders } from './pages/admin/ManageOrders';
import { AdminNotifications } from './pages/admin/AdminNotifications';
import { AdminSettings } from './pages/admin/AdminSettings';
import { AdminReports } from './pages/admin/AdminReports';
import { AdminPayments } from './pages/admin/AdminPayments';
import { SellerApplication } from './pages/seller/SellerApplication';
import { SellerApplicationStatus } from './pages/seller/SellerApplicationStatus';
import { SellerDashboard } from './pages/seller/SellerDashboard';
import { MyCrops } from './pages/seller/MyCrops';
import { AddCrop } from './pages/seller/AddCrop';
import { SellerOrders } from './pages/seller/SellerOrders';
import { SellerCustomers } from './pages/seller/SellerCustomers';
import { SellerEarnings } from './pages/seller/SellerEarnings';
import { SellerProfile } from './pages/seller/SellerProfile';
import { SellerNotifications } from './pages/seller/SellerNotifications';
import { SellerAssistant } from './pages/seller/SellerAssistant';

function NotFound() {
  return <main className="container grid min-h-[65vh] place-content-center py-16 text-center">
    <p className="text-xs font-bold uppercase tracking-[.15em] text-[#0b6b3a]">404 · Page not found</p>
    <h1 className="serif mt-3 text-3xl font-bold text-[#122018]">We couldn’t find that page.</h1>
    <a href="/" className="mt-5 text-sm font-bold text-[#0b6b3a] underline">Return to FarmCrop</a>
  </main>;
}

export default function App() {
  const [cart, setCart] = useState<CartItem[]>([]);
  const [cartOpen, setCartOpen] = useState(false);
  const location = useLocation();
  const isPortalPath = location.pathname.startsWith('/admin') || location.pathname.startsWith('/seller');
  const cartCount = cart.reduce((sum, item) => sum + item.quantity, 0);

  function addToCart(item: CartItem) {
    setCart((current) => {
      const found = current.find((cartItem) => cartItem.id === item.id);
      return found
        ? current.map((cartItem) => cartItem.id === item.id ? { ...cartItem, quantity: cartItem.quantity + 1 } : cartItem)
        : [...current, { ...item, quantity: 1 }];
    });
  }

  return <div className="min-h-screen overflow-x-hidden">
    {!isPortalPath && <Navbar cartCount={cartCount} onCartClick={() => setCartOpen(true)} />}
    <Routes>
      <Route path="/" element={<Home cart={cart} addToCart={addToCart} />} />
      <Route path="/marketplace" element={<Marketplace cart={cart} addToCart={addToCart} />} />
      <Route path="/farmers" element={<Farmers />} />
      <Route path="/login" element={<Login />} />
      <Route path="/verify-phone" element={<VerifyPhone />} />
      <Route element={<ProtectedRoute />}><Route path="/profile" element={<Profile />} /></Route>

      <Route path="/admin" element={<PortalShell role="admin" />}>
        <Route index element={<Navigate to="dashboard" replace />} />
        <Route path="dashboard" element={<AdminDashboard />} />
        <Route path="applications" element={<SellerApplications />} />
        <Route path="applications/:id" element={<ApplicationDetails />} />
        <Route path="sellers" element={<ManageSellers />} />
        <Route path="users" element={<ManageBuyers />} />
        <Route path="buyers" element={<ManageBuyers />} />
        <Route path="crops" element={<ManageCrops />} />
        <Route path="orders" element={<ManageOrders />} />
        <Route path="payments" element={<AdminPayments />} />
        <Route path="reports" element={<AdminReports />} />
        <Route path="notifications" element={<AdminNotifications />} />
        <Route path="settings" element={<AdminSettings />} />
      </Route>

      <Route path="/seller/application" element={<SellerApplication />} />
      <Route path="/seller/apply" element={<SellerApplication />} />
      <Route path="/seller/application-status" element={<SellerApplicationStatus />} />
      <Route path="/seller" element={<PortalShell role="seller" />}>
        <Route index element={<Navigate to="dashboard" replace />} />
        <Route path="dashboard" element={<SellerDashboard />} />
        <Route path="crops" element={<MyCrops />} />
        <Route path="crops/new" element={<AddCrop />} />
        <Route path="crops/:id/edit" element={<AddCrop />} />
        <Route path="orders" element={<SellerOrders />} />
        <Route path="customers" element={<SellerCustomers />} />
        <Route path="earnings" element={<SellerEarnings />} />
        <Route path="ai-assistant" element={<SellerAssistant />} />
        <Route path="notifications" element={<SellerNotifications />} />
        <Route path="profile" element={<SellerProfile />} />
        <Route path="settings" element={<SellerProfile />} />
      </Route>
      <Route path="*" element={<NotFound />} />
    </Routes>
    {!isPortalPath && <Footer />}
    {!isPortalPath && cartOpen && <CartDrawer items={cart} onClose={() => setCartOpen(false)} onChange={setCart} />}
  </div>;
}
