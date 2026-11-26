import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { usePortal } from '../../features/portals/PortalContext';
import type { SellerApplicationStatus } from '../../features/portals/types';
import { DataTable, EmptyState, PageHeader, StatusBadge, formatDate, inputClassName } from '../../features/portals/components/PortalUI';

const filters: Array<{ label: string; value: 'ALL' | SellerApplicationStatus }> = [
  { label: 'All applications', value: 'ALL' },
  { label: 'Pending', value: 'PENDING' },
  { label: 'Under review', value: 'UNDER_REVIEW' },
  { label: 'Approved', value: 'APPROVED' },
  { label: 'Rejected', value: 'REJECTED' },
  { label: 'Resubmission required', value: 'RESUBMISSION_REQUIRED' },
];

export function SellerApplications() {
  const { applications } = usePortal();
  const [filter, setFilter] = useState<'ALL' | SellerApplicationStatus>('ALL');
  const [search, setSearch] = useState('');
  const filtered = useMemo(() => applications.filter((application) => {
    const matchesStatus = filter === 'ALL' || application.status === filter;
    const query = search.trim().toLowerCase();
    const matchesSearch = !query || [
      application.id,
      application.fullName,
      application.email,
      application.phone,
      application.farmName,
      application.state,
      application.district,
    ].some((value) => value.toLowerCase().includes(query));
    return matchesStatus && matchesSearch;
  }).sort((a, b) => b.submittedAt.localeCompare(a.submittedAt)), [applications, filter, search]);

  return <div>
    <PageHeader eyebrow="Verification" title="Seller Applications" description="Review seller details and uploaded-document selections before granting marketplace access." />
    <div className="mb-4 rounded-xl border border-[#f1e4be] bg-[#fffaf0] px-4 py-3 text-xs leading-5 text-[#6d5c31]">
      Local demo data only. Selected documents are not uploaded or stored; never use real identity documents in this prototype.
    </div>
    <div className="mb-4 grid gap-3 sm:grid-cols-[minmax(0,1fr)_240px]">
      <label className="sr-only" htmlFor="application-search">Search applications</label>
      <input id="application-search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search name, email, farm, or application ID" className={inputClassName} />
      <label className="sr-only" htmlFor="application-status-filter">Filter by application status</label>
      <select id="application-status-filter" value={filter} onChange={(event) => setFilter(event.target.value as 'ALL' | SellerApplicationStatus)} className={inputClassName}>
        {filters.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
      </select>
    </div>
    {filtered.length === 0 ? <EmptyState title={applications.length ? 'No matching applications' : 'No applications submitted'} description={applications.length ? 'Adjust the search or status filter.' : 'Seller submissions will be saved in this browser session and appear here for review.'} /> : <DataTable headers={['Application ID', 'Seller', 'Contact', 'Farm / business', 'Location', 'Applied', 'Status', 'Action']}>
      {filtered.map((application) => <tr key={application.id} className="text-[#53635a]">
        <td className="px-4 py-3 font-mono text-xs">{application.id.slice(0, 16)}</td>
        <td className="px-4 py-3 font-semibold text-[#263a2d]">{application.fullName}</td>
        <td className="px-4 py-3"><span className="block">{application.email}</span><span className="text-xs">{application.phone}</span></td>
        <td className="px-4 py-3">{application.farmName}</td>
        <td className="px-4 py-3">{application.village}, {application.district}</td>
        <td className="px-4 py-3">{formatDate(application.submittedAt)}</td>
        <td className="px-4 py-3"><StatusBadge status={application.status} /></td>
        <td className="px-4 py-3"><Link to={`/admin/applications/${application.id}`} className="font-bold text-[#0b6b3a]">Open</Link></td>
      </tr>)}
    </DataTable>}
  </div>;
}
