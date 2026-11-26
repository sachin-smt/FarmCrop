import { useState } from 'react';
import { Route, Routes } from 'react-router-dom';
import { Navbar } from './components/layout/Navbar';
import { Footer } from './components/layout/Footer';
import { Home } from './pages/Home';
import { Marketplace } from './pages/Marketplace';
import { Farmers } from './pages/Farmers';
import { CartDrawer } from './components/cart/CartDrawer';
import { Login } from './pages/Login';
import { VerifyPhone } from './pages/VerifyPhone';
import { Profile } from './pages/Profile';
import { ProtectedRoute } from './components/auth/ProtectedRoute';
import { SellerAccessGuard } from './components/seller/SellerAccessGuard';
import { SellerApplication } from './pages/seller/SellerApplication';
import { SellerApplicationStatus } from './pages/seller/SellerApplicationStatus';
import { SellerDashboard } from './pages/seller/SellerDashboard';
import type { CartItem } from './types';

export default function App() {
  const [cart, setCart] = useState<CartItem[]>([]);
  const [cartOpen, setCartOpen] = useState(false);
  const addToCart = (item: CartItem) => setCart((current) => {
    const found = current.find((cartItem) => cartItem.id === item.id);
    return found ? current.map((cartItem) => cartItem.id === item.id ? { ...cartItem, quantity: cartItem.quantity + 1 } : cartItem) : [...current, { ...item, quantity: 1 }];
  });
  return <div className="min-h-screen overflow-x-hidden"><Navbar cartCount={cart.reduce((sum, item) => sum + item.quantity, 0)} onCartClick={() => setCartOpen(true)} /><Routes><Route path="/" element={<Home cart={cart} addToCart={addToCart} />} /><Route path="/marketplace" element={<Marketplace cart={cart} addToCart={addToCart} />} /><Route path="/farmers" element={<Farmers />} /><Route path="/login" element={<Login />} /><Route path="/verify-phone" element={<VerifyPhone />} /><Route element={<ProtectedRoute />}><Route path="/profile" element={<Profile />} /><Route path="/seller/apply" element={<SellerApplication />} /><Route path="/seller/application-status" element={<SellerApplicationStatus />} /></Route><Route element={<SellerAccessGuard />}><Route path="/seller" element={<SellerDashboard />} /></Route></Routes><Footer />{cartOpen && <CartDrawer items={cart} onClose={() => setCartOpen(false)} onChange={setCart} />}</div>;
}
