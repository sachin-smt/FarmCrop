import {
  Banknote,
  ClipboardCheck,
  Package,
  ShoppingBag,
  Sprout,
  Store,
  Users,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { usePortal } from '../../features/portals/PortalContext';
import { PageHeader, MetricCard, Panel, DataTable, EmptyState, StatusBadge, formatDate } from '../../features/portals/components/PortalUI';

const latestPerSeller = (applications: ReturnType<typeof usePortal>['applications']) => {
  const latest = new Map<string, (typeof applications)[number]>();
  applications.forEach((application) => {
    const previous = latest.get(application.sellerId);
    if (!previous || application.submittedAt > previous.submittedAt) latest.set(application.sellerId, application);
  });
  return [...latest.values()];
};

export function AdminDashboard() {
  const { applications, crops } = usePortal();
  const latest = latestPerSeller(applications);
  const pending = applications.filter((application) => ['PENDING', 'UNDER_REVIEW'].includes(application.status));
  const counts = ['PENDING', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'RESUBMISSION_REQUIRED', 'SUSPENDED'].map((status) => ({
    status,
    count: latest.filter((application) => application.status === status).length,
  }));
  const activeCrops = crops.filter((crop) => crop.availability === 'ACTIVE').length;
  const recent = [...applications].sort((a, b) => b.submittedAt.localeCompare(a.submittedAt)).slice(0, 5);
  const maxCount = Math.max(1, ...counts.map(({ count }) => count));

  return <div>
    <PageHeader eyebrow="Marketplace overview" title="Admin Dashboard" description="Review local demo activity and monitor seller application decisions." />
    <div className="mb-4 rounded-xl border border-[#f1e4be] bg-[#fffaf0] px-4 py-3 text-xs leading-5 text-[#6d5c31]">
      Local demo workspace. User, buyer, order, and payment totals are not connected to a backend and are intentionally not estimated.
    </div>
    <div className="grid grid-cols-2 gap-3 lg:grid-cols-3 2xl:grid-cols-5">
      <MetricCard label="Registered users" value="Not connected" note="Spring Boot integration pending" icon={<Users size={18} />} />
      <MetricCard label="Total buyers" value="Not connected" note="Buyer records are not available" icon={<Users size={18} />} />
      <MetricCard label="Total sellers" value={latest.filter((application) => ['APPROVED', 'SUSPENDED'].includes(application.status)).length} note="From local applications" icon={<Store size={18} />} />
      <MetricCard label="Pending applications" value={pending.length} note="Pending or under review" icon={<ClipboardCheck size={18} />} />
      <MetricCard label="Approved sellers" value={latest.filter((application) => application.status === 'APPROVED').length} note="Local demo records" icon={<Store size={18} />} />
      <MetricCard label="Rejected applications" value={applications.filter((application) => application.status === 'REJECTED').length} note="All review records" icon={<ClipboardCheck size={18} />} />
      <MetricCard label="Active crop listings" value={activeCrops} note="Local seller entries" icon={<Sprout size={18} />} />
      <MetricCard label="Total orders" value="Not connected" note="No order data available" icon={<ShoppingBag size={18} />} />
      <MetricCard label="Completed orders" value="Not connected" note="No order data available" icon={<Package size={18} />} />
      <MetricCard label="Total revenue" value="Not connected" note="No financial data available" icon={<Banknote size={18} />} />
    </div>
    <div className="mt-5 grid gap-4 xl:grid-cols-2">
      <Panel title="Seller application overview">
        {latest.length === 0 ? <EmptyState title="No seller applications yet" description="Applications submitted through the local Seller portal will appear here. No sample business data is preloaded." /> : <div className="space-y-4">
          {counts.map(({ status, count }) => <div key={status} className="grid grid-cols-[130px_minmax(0,1fr)_32px] items-center gap-3 text-xs">
            <span className="truncate font-semibold text-[#53635a]">{status.replaceAll('_', ' ')}</span>
            <div className="h-2 overflow-hidden rounded-full bg-[#edf0e9]"><div className="h-full rounded-full bg-[#0b6b3a]" style={{ width: `${Math.max(count ? 8 : 0, (count / maxCount) * 100)}%` }} /></div>
            <span className="text-right font-bold text-[#263a2d]">{count}</span>
          </div>)}
        </div>}
      </Panel>
      <Panel title="Crop category distribution">
        {crops.length === 0 ? <EmptyState title="No crop listings yet" description="Category totals will be calculated from seller listings. This chart does not contain fabricated data." /> : <div className="space-y-3">
          {[...new Set(crops.map((crop) => crop.category))].map((category) => {
            const count = crops.filter((crop) => crop.category === category).length;
            return <div key={category} className="flex items-center justify-between rounded-xl bg-[#f7f9f5] px-4 py-3 text-sm"><span>{category}</span><b>{count}</b></div>;
          })}
        </div>}
      </Panel>
      <Panel title="Order activity">
        <EmptyState title="Order activity is not connected" description="Order metrics will appear after the backend order service is available." />
      </Panel>
      <Panel title="Recent seller applications" action={<Link to="/admin/applications" className="text-xs font-bold text-[#0b6b3a]">View all</Link>}>
        {recent.length === 0 ? <EmptyState title="Nothing to review" description="New local demo applications will be listed here." /> : <DataTable headers={['Seller', 'Farm', 'Submitted', 'Status', '']}>
          {recent.map((application) => <tr key={application.id} className="text-[#53635a]">
            <td className="px-4 py-3 font-semibold text-[#263a2d]">{application.fullName}</td>
            <td className="px-4 py-3">{application.farmName}</td>
            <td className="px-4 py-3">{formatDate(application.submittedAt)}</td>
            <td className="px-4 py-3"><StatusBadge status={application.status} /></td>
            <td className="px-4 py-3"><Link to={`/admin/applications/${application.id}`} className="font-bold text-[#0b6b3a]">Review</Link></td>
          </tr>)}
        </DataTable>}
      </Panel>
    </div>
    <Panel title="Pending approval notifications">
      {pending.length === 0 ? <p className="text-sm text-[#66736b]">No applications are waiting for review.</p> : <ul className="space-y-3">{pending.slice(0, 5).map((application) => <li key={application.id} className="flex flex-col gap-2 rounded-xl bg-[#f7f9f5] p-3 sm:flex-row sm:items-center sm:justify-between"><span className="text-sm"><b>{application.fullName}</b> submitted an application.</span><Link to={`/admin/applications/${application.id}`} className="text-xs font-bold text-[#0b6b3a]">Open application</Link></li>)}</ul>}
    </Panel>
  </div>;
}
