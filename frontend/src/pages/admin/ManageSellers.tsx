import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { usePortal } from '../../features/portals/PortalContext';
import { DataTable, EmptyState, PageHeader, StatusBadge, formatDate, inputClassName, secondaryButtonClassName } from '../../features/portals/components/PortalUI';

export function ManageSellers() {
  const { applications, crops, setSellerSuspended } = usePortal();
  const [search, setSearch] = useState('');
  const latest = useMemo(() => {
    const records = new Map<string, (typeof applications)[number]>();
    applications.forEach((application) => {
      const current = records.get(application.sellerId);
      if (!current || application.submittedAt > current.submittedAt) records.set(application.sellerId, application);
    });
    const query = search.trim().toLowerCase();
    return [...records.values()].filter((seller) => ['APPROVED', 'SUSPENDED'].includes(seller.status))
      .filter((seller) => !query || [
        seller.fullName,
        seller.id,
        seller.farmName,
        seller.state,
        seller.district,
      ].some((value) => value.toLowerCase().includes(query)));
  }, [applications, search]);

  function toggleSuspension(sellerId: string, currentlySuspended: boolean) {
    const verb = currentlySuspended ? 'reactivate' : 'suspend';
    if (!window.confirm(`Are you sure you want to ${verb} this seller account?`)) return;
    setSellerSuspended(sellerId, !currentlySuspended);
  }

  return <div>
    <PageHeader eyebrow="Marketplace accounts" title="All Sellers" description="Seller records and application status are shown here for frontend review." />
    <label htmlFor="seller-search" className="sr-only">Search sellers</label>
    <input id="seller-search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search seller, ID, farm, or location" className={`${inputClassName} mb-4 max-w-xl`} />
    {latest.length === 0 ? <EmptyState title="No seller accounts yet" description="Approved seller records will appear here after an application is reviewed." /> : <DataTable headers={['Seller', 'Seller ID', 'Location', 'Verification', 'Listings', 'Account', 'Registered', 'Actions']}>
      {latest.map((seller) => {
        const listingCount = crops.filter((crop) => crop.sellerId === seller.sellerId).length;
        const suspended = seller.status === 'SUSPENDED';
        return <tr key={seller.sellerId} className="text-[#53635a]">
          <td className="px-4 py-3 font-semibold text-[#263a2d]">{seller.fullName}<span className="block text-xs font-normal">{seller.farmName}</span></td>
          <td className="px-4 py-3 font-mono text-xs">{seller.sellerId}</td>
          <td className="px-4 py-3">{seller.district}, {seller.state}</td>
          <td className="px-4 py-3"><StatusBadge status={seller.status} /></td>
          <td className="px-4 py-3">{listingCount}</td>
          <td className="px-4 py-3"><StatusBadge status={suspended ? 'SUSPENDED' : 'ACTIVE'} /></td>
          <td className="px-4 py-3">{formatDate(seller.submittedAt)}</td>
          <td className="px-4 py-3"><div className="flex flex-wrap gap-2">
            <Link to={`/admin/applications/${seller.id}`} className={secondaryButtonClassName}>View</Link>
            {['APPROVED', 'SUSPENDED'].includes(seller.status) && <button onClick={() => toggleSuspension(seller.sellerId, suspended)} className={secondaryButtonClassName}>{suspended ? 'Reactivate' : 'Suspend'}</button>}
          </div></td>
        </tr>;
      })}
    </DataTable>}
  </div>;
}
