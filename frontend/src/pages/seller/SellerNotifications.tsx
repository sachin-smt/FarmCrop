import { Bell, ClipboardCheck } from 'lucide-react';
import { Link } from 'react-router-dom';
import { usePortal } from '../../features/portals/PortalContext';
import { EmptyState, PageHeader, Panel, StatusBadge, formatDate } from '../../features/portals/components/PortalUI';

export function SellerNotifications() {
  const { latestApplication, crops } = usePortal();
  const application = latestApplication;
  return <div>
    <PageHeader eyebrow="Updates" title="Notifications" description="Status changes are shown here from your local demo application." />
    <div className="space-y-4">
      <Panel title="Application updates">
        {!application ? <EmptyState title="No updates" description="Submit an application to see its status here." action={<Link to="/seller/apply" className="text-xs font-bold text-[#0b6b3a]">Start application</Link>} /> : <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
          <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-[#e8f5ec] text-[#0b6b3a]"><ClipboardCheck size={18} /></span>
          <div className="min-w-0 flex-1"><p className="text-sm font-bold text-[#263a2d]">{application.status.replaceAll('_', ' ')}</p><p className="mt-1 text-xs text-[#66736b]">Application {application.id} · Updated {formatDate(application.updatedAt)}</p></div>
          <StatusBadge status={application.status} />
          <Link to="/seller/application-status" className="text-xs font-bold text-[#0b6b3a]">View status</Link>
        </div>}
      </Panel>
      <Panel title="Crop listing activity">
        {crops.length ? <p className="text-sm text-[#66736b]">{crops.length} local listing{crops.length === 1 ? '' : 's'} in this browser session.</p> : <p className="flex items-center gap-2 text-sm text-[#66736b]"><Bell size={16} /> No crop listing activity yet.</p>}
      </Panel>
    </div>
  </div>;
}
