import { useState } from 'react';
import { ArrowLeft, Check, FileCheck2 } from 'lucide-react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { usePortal } from '../../features/portals/PortalContext';
import type { ReviewAction, SellerApplicationRecord } from '../../features/portals/types';
import { EmptyState, PageHeader, Panel, StatusBadge, formatDate, inputClassName, primaryButtonClassName, secondaryButtonClassName } from '../../features/portals/components/PortalUI';

const reviewOptions: Array<{ action: ReviewAction; label: string }> = [
  { action: 'UNDER_REVIEW', label: 'Mark under review' },
  { action: 'APPROVED', label: 'Approve seller' },
  { action: 'REJECTED', label: 'Reject application' },
  { action: 'RESUBMISSION_REQUIRED', label: 'Request resubmission' },
];

function ApplicationDecisionDialog({ application, initialAction, onClose, onConfirm }: {
  application: SellerApplicationRecord;
  initialAction: ReviewAction;
  onClose: () => void;
  onConfirm: (action: ReviewAction, note: string) => void;
}) {
  const [action, setAction] = useState<ReviewAction>(initialAction);
  const [note, setNote] = useState('');
  const requiresNote = action === 'REJECTED' || action === 'RESUBMISSION_REQUIRED';

  return <div className="fixed inset-0 z-50 grid place-items-center bg-[#122018]/50 p-4" role="presentation">
    <section role="dialog" aria-modal="true" aria-labelledby="decision-title" className="w-full max-w-lg rounded-2xl bg-white p-5 shadow-xl sm:p-7">
      <h2 id="decision-title" className="text-xl font-bold text-[#122018]">Confirm application decision</h2>
      <p className="mt-2 text-sm text-[#66736b]">Choose a review action for {application.fullName}. The decision will be saved in this browser session.</p>
      <label className="mt-5 block text-xs font-bold text-[#31533d]">Decision
        <select value={action} onChange={(event) => { setAction(event.target.value as ReviewAction); setNote(''); }} className={`${inputClassName} mt-2`}>
          {reviewOptions.map((option) => <option key={option.action} value={option.action}>{option.label}</option>)}
        </select>
      </label>
      {requiresNote && <label className="mt-4 block text-xs font-bold text-[#31533d]">{action === 'REJECTED' ? 'Rejection reason (required)' : 'Missing documents or information (required)'}
        <textarea required value={note} onChange={(event) => setNote(event.target.value)} rows={4} className={`${inputClassName} mt-2 resize-y`} placeholder={action === 'REJECTED' ? 'Explain why this application cannot be approved.' : 'Specify the additional documents or information required.'} />
      </label>}
      <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <button type="button" onClick={onClose} className={secondaryButtonClassName}>Cancel</button>
        <button type="button" disabled={requiresNote && !note.trim()} onClick={() => onConfirm(action, note)} className={primaryButtonClassName}>Confirm decision</button>
      </div>
    </section>
  </div>;
}

function InformationRow({ label, value }: { label: string; value: string }) {
  return <div className="min-w-0 border-b border-[#edf0e9] py-3 last:border-b-0">
    <dt className="text-[10px] font-bold uppercase tracking-[.1em] text-[#87928a]">{label}</dt>
    <dd className="mt-1 break-words text-sm font-medium text-[#263a2d]">{value || 'Not provided'}</dd>
  </div>;
}

export function ApplicationDetails() {
  const { id = '' } = useParams();
  const { applications, reviewApplication } = usePortal();
  const application = applications.find((item) => item.id === id);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogAction, setDialogAction] = useState<ReviewAction>('APPROVED');
  const navigate = useNavigate();

  if (!application) return <EmptyState title="Application not found" description="It may have been cleared when this browser session ended." action={<Link to="/admin/applications" className={secondaryButtonClassName}>Back to applications</Link>} />;

  function decide(action: ReviewAction, note: string) {
    reviewApplication(id, action, note);
    setDialogOpen(false);
    if (action === 'APPROVED' || action === 'REJECTED' || action === 'RESUBMISSION_REQUIRED') navigate('/admin/applications');
  }

  const documents = [
    ['Identity proof', application.documents.identity],
    ['Address proof', application.documents.address],
    ['Farm ownership / authorization', application.documents.farmAuthorization],
  ] as const;

  return <div>
    <PageHeader eyebrow="Seller verification" title="Application details" description={`Review ${application.id} and decide whether this seller may access marketplace management.`} action={<Link to="/admin/applications" className={secondaryButtonClassName}><ArrowLeft size={16} /> Applications</Link>} />
    <div className="mb-5 flex flex-wrap items-center gap-3 rounded-2xl border border-[#e5eae3] bg-white p-4">
      <StatusBadge status={application.status} />
      <span className="text-xs text-[#66736b]">Submitted {formatDate(application.submittedAt)}</span>
      <span className="ml-auto text-xs text-[#66736b]">Demo session only</span>
    </div>
    {(application.status === 'PENDING' || application.status === 'UNDER_REVIEW') && <div className="mb-5 flex flex-wrap gap-2">
      {reviewOptions.map((option) => <button key={option.action} onClick={() => { setDialogAction(option.action); setDialogOpen(true); }} className={option.action === 'APPROVED' ? primaryButtonClassName : secondaryButtonClassName} aria-label={option.label}>{option.label}</button>)}
    </div>}
    <div className="grid gap-4 xl:grid-cols-2">
      <Panel title="Seller information">
        <dl className="grid gap-x-6 sm:grid-cols-2">
          <InformationRow label="Full name" value={application.fullName} />
          <InformationRow label="Email" value={application.email} />
          <InformationRow label="Phone" value={application.phone} />
          <InformationRow label="Farm / business name" value={application.farmName} />
          <InformationRow label="State" value={application.state} />
          <InformationRow label="District" value={application.district} />
          <InformationRow label="Village" value={application.village} />
          <InformationRow label="PIN code" value={application.pinCode} />
          <InformationRow label="Address" value={application.address} />
          <InformationRow label="Experience" value={application.experience} />
          <InformationRow label="Crops grown" value={application.cropsGrown} />
          <InformationRow label="Seller remarks" value={application.description} />
        </dl>
      </Panel>
      <div className="space-y-4">
        <Panel title="Verification documents">
          <p className="mb-3 text-xs leading-5 text-[#66736b]">Only selection status is retained. The local demo never uploads or saves document contents.</p>
          <ul className="space-y-2">{documents.map(([label, selected]) => <li key={label} className="flex items-center justify-between gap-3 rounded-xl bg-[#f7f9f5] px-3 py-3 text-sm">
            <span className="flex items-center gap-2"><FileCheck2 size={17} className="text-[#0b6b3a]" />{label}</span>
            <span className={`text-xs font-bold ${selected ? 'text-[#17643a]' : 'text-[#87928a]'}`}>{selected ? 'Selected locally' : 'Not selected'}</span>
          </li>)}</ul>
        </Panel>
        {(application.adminRemarks || application.missingInformation) && <Panel title="Review notes">
          {application.adminRemarks && <p className="text-sm leading-6 text-[#a94837]"><b>Rejection reason:</b> {application.adminRemarks}</p>}
          {application.missingInformation && <p className="text-sm leading-6 text-[#8a5d00]"><b>Requested information:</b> {application.missingInformation}</p>}
        </Panel>}
        <Panel title="Review history">
          <ol className="space-y-4">{application.history.map((entry, index) => <li key={`${entry.createdAt}-${index}`} className="flex gap-3">
            <span className="mt-1 grid size-6 shrink-0 place-items-center rounded-full bg-[#e8f5ec] text-[#0b6b3a]"><Check size={13} /></span>
            <div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-2"><StatusBadge status={entry.action} /><time className="text-[10px] text-[#87928a]">{formatDate(entry.createdAt)}</time></div>{entry.note && <p className="mt-1 break-words text-xs leading-5 text-[#66736b]">{entry.note}</p>}</div>
          </li>)}</ol>
        </Panel>
      </div>
    </div>
    {dialogOpen && <ApplicationDecisionDialog key={dialogAction} application={application} initialAction={dialogAction} onClose={() => setDialogOpen(false)} onConfirm={decide} />}
  </div>;
}
