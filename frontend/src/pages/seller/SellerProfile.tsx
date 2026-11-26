import { Link } from 'react-router-dom';
import { usePortal } from '../../features/portals/PortalContext';
import { EmptyState, PageHeader, Panel, StatusBadge } from '../../features/portals/components/PortalUI';

export function SellerProfile() {
  const { latestApplication } = usePortal();
  if (!latestApplication) return <div><PageHeader eyebrow="Account" title="Profile & Settings" /><EmptyState title="No application details" description="Seller profile details will appear here after an application is submitted." action={<Link to="/seller/application" className="font-bold text-[#0b6b3a]">Open application</Link>} /></div>;

  return <div>
    <PageHeader eyebrow="Account" title="Profile & Settings" description="Profile details shown here come from the seller application." />
    <div className="grid gap-4 lg:grid-cols-2">
      <Panel title="Seller profile">
        <dl className="space-y-4 text-sm">
          {[['Full name', latestApplication.fullName], ['Email', latestApplication.email], ['Phone', latestApplication.phone], ['Farm / business', latestApplication.farmName], ['Location', `${latestApplication.village}, ${latestApplication.district}, ${latestApplication.state}`]].map(([label, value]) => <div key={label}><dt className="text-xs text-[#87928a]">{label}</dt><dd className="mt-1 break-words font-semibold text-[#263a2d]">{value || 'Not provided'}</dd></div>)}
          <div><dt className="text-xs text-[#87928a]">Verification</dt><dd className="mt-2"><StatusBadge status={latestApplication.status} /></dd></div>
        </dl>
      </Panel>
      <Panel title="Account and privacy">
        <p className="text-sm leading-6 text-[#53635a]">Profile editing and notifications will be available after backend integration.</p>
        <p className="mt-3 text-sm leading-6 text-[#53635a]">Application data is stored in this browser session. Selected document files are not uploaded or persisted.</p>
        <Link to="/seller/application-status" className="mt-5 inline-block text-xs font-bold text-[#0b6b3a]">Open verification status</Link>
      </Panel>
    </div>
  </div>;
}
