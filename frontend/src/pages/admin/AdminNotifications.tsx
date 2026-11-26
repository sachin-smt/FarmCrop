import { Bell, ClipboardCheck } from 'lucide-react';
import { Link } from 'react-router-dom';
import { usePortal } from '../../features/portals/PortalContext';
import { EmptyState, PageHeader, Panel, StatusBadge, formatDate } from '../../features/portals/components/PortalUI';

export function AdminNotifications() {
  const { applications, crops } = usePortal();
  const items = applications
    .filter((application) => ['PENDING', 'RESUBMISSION_REQUIRED'].includes(application.status))
    .map((application) => ({
      id: application.id,
      title: application.status === 'PENDING' ? 'Seller application awaiting review' : 'Seller resubmission received',
      description: `${application.fullName} · ${application.farmName}`,
      status: application.status,
      date: application.updatedAt,
      href: `/admin/applications/${application.id}`,
    }));

  return <div>
    <PageHeader eyebrow="Updates" title="Notifications" description="Notifications are derived from locally submitted applications and marketplace listings." />
    <div className="space-y-4">
      <Panel title="Seller application updates">
        {items.length === 0 ? <EmptyState title="You're all caught up" description="New applications or resubmissions will appear here." /> : <ul className="divide-y divide-[#edf0e9]">{items.map((item) => <li key={item.id} className="flex flex-col gap-3 py-4 first:pt-0 last:pb-0 sm:flex-row sm:items-center">
          <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-[#e8f5ec] text-[#0b6b3a]"><ClipboardCheck size={19} /></span>
          <div className="min-w-0 flex-1"><p className="text-sm font-bold text-[#263a2d]">{item.title}</p><p className="mt-1 text-xs text-[#66736b]">{item.description}</p></div>
          <div className="flex items-center gap-3"><StatusBadge status={item.status} /><time className="text-[10px] text-[#87928a]">{formatDate(item.date)}</time><Link to={item.href} className="text-xs font-bold text-[#0b6b3a]">Review</Link></div>
        </li>)}</ul>}
      </Panel>
      <Panel title="Marketplace activity">
        {crops.length === 0 ? <p className="flex items-center gap-2 text-sm text-[#66736b]"><Bell size={16} /> No crop listing activity in this browser session.</p> : <p className="text-sm text-[#66736b]">{crops.length} locally created listing{crops.length === 1 ? '' : 's'} available for review.</p>}
      </Panel>
    </div>
  </div>;
}
