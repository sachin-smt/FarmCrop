export function SellerDashboard() {
  return <main className="container py-8 sm:py-16">
    <p className="text-xs font-bold uppercase tracking-[.16em] text-[#0b6b3a]">Seller mode</p>
    <h1 className="serif mt-2 text-3xl font-bold text-[#064e2a] sm:text-4xl">Seller Dashboard</h1>
    <p className="mt-3 max-w-2xl text-sm leading-6 text-[#66736b] sm:mt-4 sm:text-base sm:leading-7">Seller tools will be added in later phases. This protected route is available only when an approved Seller status is supplied by a trusted backend.</p>
    <div className="mt-6 grid gap-3 sm:mt-8 sm:grid-cols-3">
      {['Listings', 'Orders', 'Earnings'].map((label) => <div key={label} className="min-w-0 rounded-2xl border border-[#e5eae3] bg-white p-4 shadow-sm sm:p-5"><p className="text-xs font-bold uppercase tracking-[.12em] text-[#0b6b3a]">{label}</p><p className="mt-2 text-sm text-[#87928a]">Not connected</p></div>)}
    </div>
  </main>;
}
