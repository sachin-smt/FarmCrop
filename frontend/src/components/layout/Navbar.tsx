import { useState } from 'react';
import { ChevronDown, Leaf, Menu, MapPin, Search, ShoppingCart, X } from 'lucide-react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { LanguageSelector } from '../i18n/LanguageSelector';
import { useAuth } from '../../hooks/useAuth';

interface NavbarProps { cartCount: number; onCartClick: () => void; }

export function Navbar({ cartCount, onCartClick }: NavbarProps) {
  const [open, setOpen] = useState(false);
  const { t } = useTranslation();
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const nav = [
    { to: '/', label: t('nav.home') },
    { to: '/marketplace', label: t('nav.crops') },
    { to: '/farmers', label: t('nav.farmers') },
  ];
  return <header className="sticky top-0 z-40 border-b border-[#e7e8df] bg-[#fffdf8]/95 backdrop-blur-md">
    <div className="container flex min-h-[72px] items-center gap-3">
      <Link to="/" className="flex shrink-0 items-center gap-2" aria-label="FarmCrop home"><span className="grid size-10 place-items-center rounded-full bg-[#e8f5ec] text-[#0b6b3a]"><Leaf size={25} fill="currentColor" /></span><span><strong className="block text-lg leading-5 tracking-[-.04em] text-[#064e2a]">FarmCrop</strong><small className="hidden text-[9px] text-[#66736b] sm:block">From Farmers to Every Home</small></span></Link>
      <nav className="hidden items-center gap-1 lg:flex" aria-label="Primary navigation">{nav.map((item, index) => <NavLink key={item.to} to={item.to} end={index === 0} className={({ isActive }) => `rounded-full px-3 py-2 text-xs font-semibold ${isActive ? 'bg-[#e8f5ec] text-[#064e2a]' : 'text-[#3e4e44] hover:bg-[#f2f5ef]'}`}>{item.label}</NavLink>)}<a href="#about" className="rounded-full px-3 py-2 text-xs font-semibold text-[#3e4e44] hover:bg-[#f2f5ef]">{t('nav.about')}</a><a href="#support" className="rounded-full px-3 py-2 text-xs font-semibold text-[#3e4e44] hover:bg-[#f2f5ef]">{t('nav.support')}</a></nav>
      <div className="ml-auto hidden min-w-0 flex-1 items-center gap-3 xl:flex"><label className="flex min-w-[180px] flex-1 items-center gap-2 rounded-full border border-[#e1e5df] bg-white px-4 py-2.5 text-xs text-[#87928a]"><Search size={16} /><input className="min-w-0 flex-1 bg-transparent outline-none placeholder:text-[#87928a]" placeholder="Search crops, farmers, or your location..." aria-label="Search crops" /></label><button className="flex shrink-0 items-center gap-1.5 text-left text-[10px] leading-3 text-[#66736b]" aria-label="Choose delivery location"><MapPin size={19} className="text-[#0b6b3a]" /><span>Deliver to<br /><b className="text-[#122018]">Bihar, India</b></span><ChevronDown size={13} /></button></div>
      <LanguageSelector />{user && <button onClick={() => navigate('/seller/application-status')} className="hidden rounded-full border border-[#dfe7df] px-3 py-3 text-xs font-bold text-[#31533d] xl:block">Seller access</button>}<button onClick={onCartClick} className="relative grid size-10 shrink-0 place-items-center rounded-full text-[#064e2a] hover:bg-[#e8f5ec]" aria-label={`Shopping cart, ${cartCount} items`}><ShoppingCart size={20} />{cartCount > 0 && <span className="absolute right-0 top-0 grid size-4 place-items-center rounded-full bg-[#e65b3f] text-[9px] font-bold text-white">{cartCount}</span>}</button><button onClick={() => user ? void logout() : navigate('/login')} className="hidden rounded-full bg-[#0b6b3a] px-4 py-3 text-xs font-bold text-white shadow-sm hover:bg-[#064e2a] sm:block">{user ? t('nav.logout') : t('nav.login')}</button><button className="grid size-10 place-items-center rounded-full border border-[#e1e5df] lg:hidden" onClick={() => setOpen(!open)} aria-label={open ? 'Close menu' : 'Open menu'}>{open ? <X size={20} /> : <Menu size={20} />}</button>
    </div>
    {open && <div className="container border-t border-[#edf0e9] pb-4 pt-3 lg:hidden"><label className="mb-3 flex items-center gap-2 rounded-full border border-[#e1e5df] bg-white px-4 py-3 text-sm text-[#87928a]"><Search size={17} /><input className="min-w-0 flex-1 bg-transparent outline-none" placeholder={t('marketplace.search')} aria-label={t('marketplace.search')} /></label><nav className="grid gap-1" aria-label="Mobile navigation">{nav.map((item) => <NavLink key={item.to} to={item.to} onClick={() => setOpen(false)} className="rounded-xl px-3 py-3 text-sm font-semibold">{item.label}</NavLink>)}</nav></div>}
  </header>;
}
