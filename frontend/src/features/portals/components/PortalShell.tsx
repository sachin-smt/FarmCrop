import { useState, type ComponentType } from 'react';
import {
  ArrowLeft,
  Banknote,
  Bell,
  Boxes,
  ChartNoAxesCombined,
  ClipboardCheck,
  LayoutDashboard,
  Menu,
  Package,
  Plus,
  Settings,
  ShieldCheck,
  ShoppingBag,
  Sprout,
  Store,
  UserRound,
  Users,
  X,
} from 'lucide-react';
import { Link, NavLink, Outlet } from 'react-router-dom';
import { usePortal } from '../PortalContext';
import type { PortalRole } from '../types';

type Icon = ComponentType<{ size?: number; className?: string }>;

interface NavigationItem {
  label: string;
  to: string;
  icon: Icon;
}

const adminNavigation: NavigationItem[] = [
  { label: 'Dashboard', to: '/admin/dashboard', icon: LayoutDashboard },
  { label: 'Seller Applications', to: '/admin/applications', icon: ClipboardCheck },
  { label: 'All Sellers', to: '/admin/sellers', icon: Store },
  { label: 'Buyers', to: '/admin/buyers', icon: Users },
  { label: 'Crop Listings', to: '/admin/crops', icon: Sprout },
  { label: 'Orders', to: '/admin/orders', icon: ShoppingBag },
  { label: 'Payments', to: '/admin/payments', icon: Banknote },
  { label: 'Reports & Analytics', to: '/admin/reports', icon: ChartNoAxesCombined },
  { label: 'Notifications', to: '/admin/notifications', icon: Bell },
  { label: 'Settings', to: '/admin/settings', icon: Settings },
];

const sellerNavigation: NavigationItem[] = [
  { label: 'Dashboard', to: '/seller/dashboard', icon: LayoutDashboard },
  { label: 'My Crops', to: '/seller/crops', icon: Sprout },
  { label: 'Add New Crop', to: '/seller/crops/new', icon: Plus },
  { label: 'Orders', to: '/seller/orders', icon: ShoppingBag },
  { label: 'Customers', to: '/seller/customers', icon: Users },
  { label: 'Earnings', to: '/seller/earnings', icon: Banknote },
  { label: 'Verification Status', to: '/seller/application-status', icon: ShieldCheck },
  { label: 'AI Assistant', to: '/seller/ai-assistant', icon: Boxes },
  { label: 'Notifications', to: '/seller/notifications', icon: Bell },
  { label: 'Profile & Settings', to: '/seller/profile', icon: UserRound },
];

export function PortalShell({ role }: { role: PortalRole }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const { applications } = usePortal();
  const isAdmin = role === 'admin';
  const navigation = isAdmin ? adminNavigation : sellerNavigation;
  const pendingCount = applications.filter((application) => ['PENDING', 'RESUBMISSION_REQUIRED'].includes(application.status)).length;
  const portalName = isAdmin ? 'Admin Portal' : 'Seller Portal';

  return <div className="min-h-screen overflow-x-hidden bg-[#f7f8f3] text-[#122018]">
    <header className="fixed inset-x-0 top-0 z-30 flex h-16 items-center gap-3 border-b border-[#e5eae3] bg-white px-4 sm:px-6 md:pl-[17rem]">
      <button onClick={() => setMenuOpen((open) => !open)} className="grid size-10 place-items-center rounded-xl border border-[#e5eae3] text-[#31533d] md:hidden" aria-label={menuOpen ? 'Close navigation' : 'Open navigation'}>
        {menuOpen ? <X size={19} /> : <Menu size={19} />}
      </button>
      <Link to="/" className="flex items-center gap-2 font-bold text-[#064e2a]">
        <span className="grid size-9 place-items-center rounded-xl bg-[#e8f5ec]"><Sprout size={20} /></span>
        <span className="hidden sm:inline">FarmCrop</span>
      </Link>
      <span className="hidden h-6 w-px bg-[#e5eae3] sm:block" />
      <span className="text-sm font-semibold text-[#53635a]">{portalName}</span>
      <div className="ml-auto flex items-center gap-2 sm:gap-4">
        <Link to={isAdmin ? '/admin/notifications' : '/seller/notifications'} className="relative grid size-10 place-items-center rounded-xl text-[#31533d] hover:bg-[#f2f5ef]" aria-label="Notifications">
          <Bell size={19} />
          {pendingCount > 0 && <span className="absolute right-1 top-1 grid size-4 place-items-center rounded-full bg-[#d95a40] text-[9px] font-bold text-white">{pendingCount}</span>}
        </Link>
      </div>
    </header>
    {menuOpen && <button className="fixed inset-0 z-30 bg-black/30 md:hidden" onClick={() => setMenuOpen(false)} aria-label="Close navigation overlay" />}
    <aside className={`fixed bottom-0 left-0 top-16 z-40 flex w-[min(17rem,85vw)] flex-col border-r border-[#e5eae3] bg-white px-3 py-4 transition-transform md:translate-x-0 ${menuOpen ? 'translate-x-0' : '-translate-x-full'}`}>
      <div className="mb-4 rounded-2xl bg-[#f4f8f2] px-3 py-3">
        <p className="text-[10px] font-bold uppercase tracking-[.14em] text-[#0b6b3a]">FarmCrop</p>
        <p className="mt-1 text-sm font-bold text-[#263a2d]">{isAdmin ? 'Marketplace management' : 'Farmer workspace'}</p>
      </div>
      <nav className="min-h-0 flex-1 space-y-1 overflow-y-auto" aria-label={`${portalName} navigation`}>
        {navigation.map(({ label, to, icon: IconComponent }) => <NavLink
          key={to}
          to={to}
          onClick={() => setMenuOpen(false)}
          className={({ isActive }) => `flex min-h-11 items-center gap-3 rounded-xl px-3 text-sm font-semibold transition ${isActive ? 'bg-[#e8f5ec] text-[#064e2a]' : 'text-[#53635a] hover:bg-[#f5f8f4]'}`}
        >
          <IconComponent size={18} />
          <span className="min-w-0 flex-1">{label}</span>
          {label === 'Seller Applications' && pendingCount > 0 && <span className="rounded-full bg-[#d95a40] px-2 py-0.5 text-[10px] text-white">{pendingCount}</span>}
        </NavLink>)}
      </nav>
      <Link to="/" className="mt-3 flex min-h-11 items-center gap-2 rounded-xl px-3 text-xs font-semibold text-[#66736b] hover:bg-[#f5f8f4]">
        <ArrowLeft size={16} /> Back to marketplace
      </Link>
    </aside>
    <main className="min-w-0 px-4 pb-10 pt-20 sm:px-6 md:ml-[17rem] md:px-8">
      <div className="mx-auto w-full max-w-[1440px]"><Outlet /></div>
    </main>
  </div>;
}
