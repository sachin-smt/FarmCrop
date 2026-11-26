import { Link } from 'react-router-dom';

export function SellerApplication() {
  return <main className="container max-w-2xl py-16">
    <p className="text-xs font-bold uppercase tracking-[.16em] text-[#0b6b3a]">Seller access</p>
    <h1 className="serif mt-2 text-4xl font-bold text-[#064e2a]">Apply to sell on FarmCrop</h1>
    <p className="mt-4 leading-7 text-[#66736b]">Seller applications will be connected to the approved application workflow in a future backend phase. You can continue using Buyer features while this integration is prepared.</p>
    <div className="mt-8 rounded-2xl border border-[#e5eae3] bg-white p-5 text-sm text-[#53635a]">
      <p className="font-bold text-[#122018]">Application form not connected</p>
      <p className="mt-2">No application will be submitted from this screen yet. This prevents an unverified frontend action from granting Seller privileges.</p>
    </div>
    <div className="mt-6 flex flex-wrap gap-3">
      <Link to="/seller/application-status" className="rounded-xl bg-[#0b6b3a] px-5 py-3 text-sm font-bold text-white">View application status</Link>
      <Link to="/" className="rounded-xl border border-[#dfe7df] px-5 py-3 text-sm font-bold text-[#31533d]">Continue as Buyer</Link>
    </div>
  </main>;
}
