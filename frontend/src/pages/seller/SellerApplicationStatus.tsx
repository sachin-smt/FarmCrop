import { Link } from 'react-router-dom';
import { CheckCircle2, Circle, Clock3, FileWarning, ShieldCheck } from 'lucide-react';
import { usePortal } from '../../features/portals/PortalContext';
import { getSellerStatus } from '../../features/portals/services/portalAccessService';
import { EmptyState, PageHeader, StatusBadge, formatDate, primaryButtonClassName, secondaryButtonClassName } from '../../features/portals/components/PortalUI';
import type { SellerApplicationStatus as ApplicationStatus } from '../../features/portals/types';

const statusContent: Record<ApplicationStatus, { title: string; message: string; tone: string }> = {
  NOT_APPLIED: { title: 'Start your seller application', message: 'Submit your farm details and verification documents to begin the review process.', tone: 'text-[#53635a]' },
  PENDING: { title: 'Your application is waiting for Admin review.', message: 'Your application has been submitted. We will update this page when its review status changes.', tone: 'text-[#8a5d00]' },
  UNDER_REVIEW: { title: 'Your application is currently being reviewed.', message: 'The Admin team is checking the details you submitted.', tone: 'text-[#4056a1]' },
  APPROVED: { title: 'Congratulations! Your seller account has been approved.', message: 'Your application is approved. This status is informational during frontend development.', tone: 'text-[#17643a]' },
  REJECTED: { title: 'Your application was rejected. Please review the reason.', message: 'You may update the details and submit a new application.', tone: 'text-[#a94837]' },
  RESUBMISSION_REQUIRED: { title: 'Additional information or documents are required.', message: 'Review the Admin request and resubmit the missing information.', tone: 'text-[#8a5d00]' },
  SUSPENDED: { title: 'Seller application marked as suspended.', message: 'This application status does not restrict access to the seller frontend during development.', tone: 'text-[#a94837]' },
};

export function SellerApplicationStatus() {
  const { latestApplication } = usePortal();

  if (!latestApplication) return <div>
    <PageHeader eyebrow="Seller verification" title="Application status" description="Your seller application and verification progress will be shown here." />
    <EmptyState title={statusContent.NOT_APPLIED.title} description={statusContent.NOT_APPLIED.message} action={<Link to="/seller/apply" className={primaryButtonClassName}>Start application</Link>} />
  </div>;

  const status = getSellerStatus(latestApplication);
  const content = statusContent[status];
  const stages = [
    { label: 'Application submitted', complete: status !== 'NOT_APPLIED' },
    { label: 'Admin review', complete: ['UNDER_REVIEW', 'APPROVED', 'REJECTED', 'RESUBMISSION_REQUIRED', 'SUSPENDED'].includes(status) },
    { label: 'Seller decision', complete: ['APPROVED', 'REJECTED', 'RESUBMISSION_REQUIRED', 'SUSPENDED'].includes(status) },
  ];

  return <div>
    <PageHeader eyebrow="Seller verification" title="Application status" description="Track your application and read the latest review notes." />
    <section className="rounded-2xl border border-[#e5eae3] bg-white p-4 shadow-sm sm:p-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex gap-3">
          <span className={`grid size-11 shrink-0 place-items-center rounded-xl bg-[#f5f8f4] ${content.tone}`}>
            {status === 'APPROVED' ? <CheckCircle2 size={22} /> : status === 'REJECTED' || status === 'RESUBMISSION_REQUIRED' ? <FileWarning size={22} /> : status === 'UNDER_REVIEW' ? <Clock3 size={22} /> : <ShieldCheck size={22} />}
          </span>
          <div><h2 className="text-lg font-bold leading-6 text-[#263a2d]">{content.title}</h2><p className="mt-2 text-sm leading-6 text-[#66736b]">{content.message}</p></div>
        </div>
        <StatusBadge status={status} />
      </div>
      <div className="mt-6 grid gap-3 rounded-xl bg-[#f7f9f5] p-4 text-xs sm:grid-cols-2">
        <p><span className="block text-[#87928a]">Application ID</span><b className="mt-1 block font-mono text-[#263a2d]">{latestApplication.id}</b></p>
        <p><span className="block text-[#87928a]">Submitted</span><b className="mt-1 block text-[#263a2d]">{formatDate(latestApplication.submittedAt)}</b></p>
      </div>
      <ol className="mt-6 grid gap-4 sm:grid-cols-3">
        {stages.map((stage, index) => <li key={stage.label} className="flex items-start gap-3 rounded-xl border border-[#edf0e9] p-3">
          <span className={`grid size-7 shrink-0 place-items-center rounded-full ${stage.complete ? 'bg-[#e6f4ea] text-[#17643a]' : 'bg-[#f0f2ed] text-[#87928a]'}`}>{stage.complete ? <CheckCircle2 size={16} /> : <Circle size={16} />}</span>
          <div><p className="text-xs font-bold text-[#263a2d]">{stage.label}</p><p className="mt-1 text-[10px] text-[#87928a]">{index === 0 ? formatDate(latestApplication.submittedAt) : stage.complete ? 'Complete' : 'Waiting'}</p></div>
        </li>)}
      </ol>
      {latestApplication.adminRemarks && <div className="mt-5 rounded-xl border border-[#f4d0c8] bg-[#fff7f4] p-4 text-sm leading-6 text-[#8c4135]"><b>Rejection reason:</b> {latestApplication.adminRemarks}</div>}
      {latestApplication.missingInformation && <div className="mt-5 rounded-xl border border-[#f1e4be] bg-[#fffaf0] p-4 text-sm leading-6 text-[#6d5c31]"><b>Information requested:</b> {latestApplication.missingInformation}</div>}
      {latestApplication.history.length > 1 && <div className="mt-5">
        <h3 className="text-xs font-bold uppercase tracking-[.1em] text-[#66736b]">Review updates</h3>
        <ul className="mt-2 space-y-2">{latestApplication.history.slice(1).reverse().map((entry, index) => <li key={`${entry.createdAt}-${index}`} className="rounded-lg bg-[#f7f9f5] p-3 text-xs text-[#53635a]"><b>{entry.action.replaceAll('_', ' ')}</b>{entry.note && <span> — {entry.note}</span>}</li>)}</ul>
      </div>}
      <div className="mt-6 flex flex-col gap-3 sm:flex-row">
        {(status === 'REJECTED' || status === 'RESUBMISSION_REQUIRED') && <Link to="/seller/apply" className={primaryButtonClassName}>{status === 'REJECTED' ? 'Update and apply again' : 'Resubmit application'}</Link>}
        <Link to="/seller/dashboard" className={primaryButtonClassName}>Open Seller Dashboard</Link>
        <Link to="/" className={secondaryButtonClassName}>Continue to marketplace</Link>
      </div>
    </section>
  </div>;
}
