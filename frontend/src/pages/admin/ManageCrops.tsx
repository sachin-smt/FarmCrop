import { useMemo, useState } from 'react';
import { usePortal } from '../../features/portals/PortalContext';
import { DataTable, EmptyState, PageHeader, StatusBadge, inputClassName, secondaryButtonClassName } from '../../features/portals/components/PortalUI';

export function ManageCrops() {
  const { applications, crops, adminUpdateCrop, deleteCrop } = usePortal();
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('ALL');
  const categories = [...new Set(crops.map((crop) => crop.category))];
  const filtered = useMemo(() => crops.filter((crop) => {
    const matchesCategory = category === 'ALL' || crop.category === category;
    const query = search.trim().toLowerCase();
    const sellerName = applications.find((application) => application.sellerId === crop.sellerId)?.fullName ?? '';
    return matchesCategory && (!query || `${crop.name} ${crop.category} ${sellerName} ${crop.location}`.toLowerCase().includes(query));
  }), [applications, category, crops, search]);

  function toggleAvailability(id: string, status: 'ACTIVE' | 'PAUSED') {
    adminUpdateCrop(id, { availability: status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE' });
  }

  function removeListing(id: string) {
    if (window.confirm('Remove this crop listing from the local demo marketplace?')) deleteCrop(id);
  }

  return <div>
    <PageHeader eyebrow="Marketplace catalog" title="Crop Listings" description="Review locally created seller listings. No listing or purchase data is preloaded." />
    <div className="mb-4 grid gap-3 sm:grid-cols-[minmax(0,1fr)_220px]">
      <label className="sr-only" htmlFor="crop-search">Search crop listings</label>
      <input id="crop-search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search crop, seller, or location" className={inputClassName} />
      <label className="sr-only" htmlFor="crop-category">Filter by category</label>
      <select id="crop-category" value={category} onChange={(event) => setCategory(event.target.value)} className={inputClassName}><option value="ALL">All categories</option>{categories.map((item) => <option key={item}>{item}</option>)}</select>
    </div>
    {filtered.length === 0 ? <EmptyState title="No crop listings found" description={crops.length ? 'Try another search or category.' : 'Seller-created listings will appear here after approval.'} /> : <DataTable headers={['Crop', 'Category', 'Seller', 'Quantity', 'Price', 'Harvest date', 'Location', 'Status', 'Actions']}>
      {filtered.map((crop) => <tr key={crop.id} className="text-[#53635a]">
        <td className="px-4 py-3 font-semibold text-[#263a2d]">{crop.name}</td>
        <td className="px-4 py-3">{crop.category}</td>
        <td className="px-4 py-3">{applications.find((application) => application.sellerId === crop.sellerId)?.fullName ?? 'Seller'}</td>
        <td className="px-4 py-3">{crop.quantity} {crop.unit}</td>
        <td className="px-4 py-3">₹{crop.price}/{crop.unit}</td>
        <td className="px-4 py-3">{crop.harvestDate || '—'}</td>
        <td className="px-4 py-3">{crop.location}</td>
        <td className="px-4 py-3"><StatusBadge status={crop.availability} /></td>
        <td className="px-4 py-3"><div className="flex gap-2">
          <button onClick={() => toggleAvailability(crop.id, crop.availability)} className={secondaryButtonClassName}>{crop.availability === 'ACTIVE' ? 'Disable' : 'Enable'}</button>
          <button onClick={() => removeListing(crop.id)} className={secondaryButtonClassName}>Remove</button>
        </div></td>
      </tr>)}
    </DataTable>}
  </div>;
}
