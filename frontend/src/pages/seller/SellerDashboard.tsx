import { ArrowRight, Bell, Package, ShoppingBag, Sprout, Wallet } from 'lucide-react';
import { Link } from 'react-router-dom';
import { usePortal } from '../../features/portals/PortalContext';
import { EmptyState, MetricCard, PageHeader, Panel, StatusBadge, formatDate, primaryButtonClassName } from '../../features/portals/components/PortalUI';

export function SellerDashboard() {
  const { crops, latestApplication } = usePortal();
  const myCrops = crops;
  const activeCrops = myCrops.filter((crop) => crop.availability === 'ACTIVE').length;
  const recentCrops = [...myCrops].sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 4);

  return <div>
    <PageHeader eyebrow="Seller workspace" title="Seller Dashboard" description="Manage crop listings and explore the seller workspace." action={<Link to="/seller/crops/new" className={primaryButtonClassName}><Sprout size={16} /> Add a crop</Link>} />
    <div className="mb-5 flex items-start gap-3 rounded-xl border border-[#f1e4be] bg-[#fffaf0] p-4 text-xs leading-5 text-[#6d5c31]">
      <Bell size={17} className="mt-0.5 shrink-0" />
      <p>Demo workspace. Crop counts reflect this browser session; orders, customers, and earnings are not connected and no financial records are simulated.</p>
    </div>
    <div className="grid grid-cols-2 gap-3 xl:grid-cols-4">
      <MetricCard label="Total crop listings" value={myCrops.length} note="Created in this session" icon={<Sprout size={18} />} />
      <MetricCard label="Active listings" value={activeCrops} note="Available in your local catalog" icon={<Package size={18} />} />
      <MetricCard label="Pending orders" value="Not connected" note="Order service not available" icon={<ShoppingBag size={18} />} />
      <MetricCard label="Earnings" value="Not connected" note="No financial data available" icon={<Wallet size={18} />} />
    </div>
    <div className="mt-5 grid gap-4 xl:grid-cols-[1.4fr_.6fr]">
      <Panel title="Recent crop activity" action={<Link to="/seller/crops" className="inline-flex items-center gap-1 text-xs font-bold text-[#0b6b3a]">My crops <ArrowRight size={14} /></Link>}>
        {recentCrops.length === 0 ? <EmptyState title="Your crop list is empty" description="Add a listing when you are ready. Your crop details stay in this browser session." action={<Link to="/seller/crops/new" className={primaryButtonClassName}>Add your first crop</Link>} /> : <ul className="divide-y divide-[#edf0e9]">{recentCrops.map((crop) => <li key={crop.id} className="flex flex-col gap-2 py-3 first:pt-0 sm:flex-row sm:items-center sm:justify-between">
          <div><p className="text-sm font-bold text-[#263a2d]">{crop.name}</p><p className="mt-1 text-xs text-[#66736b]">{crop.category} · {crop.quantity} {crop.unit} · Created {formatDate(crop.createdAt)}</p></div>
          <StatusBadge status={crop.availability} />
        </li>)}</ul>}
      </Panel>
      <Panel title="Seller verification">
        <div className="flex items-center justify-between gap-3"><span className="text-sm font-semibold text-[#263a2d]">Application status</span><StatusBadge status={latestApplication?.status ?? 'NOT_APPLIED'} /></div>
        <p className="mt-3 text-xs leading-5 text-[#66736b]">Application status is shown for reference and does not restrict access to seller tools in this frontend phase.</p>
        <Link to="/seller/application-status" className="mt-4 inline-block text-xs font-bold text-[#0b6b3a]">View application details</Link>
      </Panel>
    </div>
    <div className="mt-5 grid gap-4 sm:grid-cols-3">
      <Panel title="Orders"><p className="text-sm text-[#66736b]">Order management will be available when the backend order service is connected.</p></Panel>
      <Panel title="Customers"><p className="text-sm text-[#66736b]">Customer records are not included in this local demo.</p></Panel>
      <Panel title="Sales overview"><p className="text-sm text-[#66736b]">No sales data is available. This panel will not estimate revenue.</p></Panel>
    </div>
  </div>;
}
