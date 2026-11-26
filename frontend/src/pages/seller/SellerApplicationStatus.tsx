import { Link } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';

const statusCopy = {
  unknown: ['Seller status unavailable', 'Seller approval has not been verified. Buyer access remains available.'],
  buyer: ['Buyer account', 'You have not been approved as a Seller.'],
  pending: ['Application pending', 'Your Seller application is awaiting administrator review.'],
  rejected: ['Application not approved', 'Review the requested next steps before applying again.'],
  suspended: ['Seller access suspended', 'Seller publishing and selling operations are currently blocked.'],
  approved: ['Seller approved', 'Seller tools are available for this account.'],
} as const;

export function SellerApplicationStatus() {
  const { profile } = useAuth();
  const status = profile?.sellerStatus || 'unknown';
  const [title, description] = statusCopy[status];

  return <main className="container max-w-2xl py-16">
    <p className="text-xs font-bold uppercase tracking-[.16em] text-[#0b6b3a]">Seller access</p>
    <h1 className="serif mt-2 text-4xl font-bold text-[#064e2a]">Application status</h1>
    <section className="mt-8 rounded-2xl border border-[#e5eae3] bg-white p-6 shadow-sm" aria-live="polite">
      <p className="text-sm font-bold uppercase tracking-[.12em] text-[#0b6b3a]">{status}</p>
      <h2 className="mt-2 text-xl font-bold text-[#122018]">{title}</h2>
      <p className="mt-3 leading-7 text-[#66736b]">{description}</p>
      <p className="mt-4 text-xs leading-5 text-[#87928a]">This status is not connected to an approval backend yet. Unknown or unavailable status never grants Seller access.</p>
    </section>
    <div className="mt-6 flex flex-wrap gap-3">
      {status !== 'approved' && <Link to="/seller/apply" className="rounded-xl bg-[#0b6b3a] px-5 py-3 text-sm font-bold text-white">Seller application</Link>}
      {status === 'approved' && <Link to="/seller" className="rounded-xl bg-[#0b6b3a] px-5 py-3 text-sm font-bold text-white">Open Seller Dashboard</Link>}
      <Link to="/" className="rounded-xl border border-[#dfe7df] px-5 py-3 text-sm font-bold text-[#31533d]">Continue as Buyer</Link>
    </div>
  </main>;
}
