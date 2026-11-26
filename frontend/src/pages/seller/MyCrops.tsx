import { useState } from 'react';
import { Link } from 'react-router-dom';
import { usePortal } from '../../features/portals/PortalContext';
import { EmptyState, PageHeader, StatusBadge, inputClassName, primaryButtonClassName, secondaryButtonClassName } from '../../features/portals/components/PortalUI';

export function MyCrops() {
  const { crops, updateCrop, deleteCrop } = usePortal();
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('ALL');
  const [availability, setAvailability] = useState('ALL');
  const myCrops = crops;
  const categories = [...new Set(myCrops.map((crop) => crop.category))];
  const filtered = myCrops.filter((crop) => {
    const matchesText = `${crop.name} ${crop.category} ${crop.description} ${crop.location}`.toLowerCase().includes(search.trim().toLowerCase());
    return matchesText && (category === 'ALL' || crop.category === category) && (availability === 'ALL' || crop.availability === availability);
  });

  function removeCrop(id: string) {
    if (window.confirm('Remove this crop listing from your local demo account?')) deleteCrop(id);
  }

  return <div>
    <PageHeader eyebrow="Crop management" title="My Crops" description="Search, update availability, edit, or remove your local crop listings." action={<Link to="/seller/crops/new" className={primaryButtonClassName}>Add new crop</Link>} />
    <div className="mb-4 grid gap-3 sm:grid-cols-3">
      <label className="sr-only" htmlFor="seller-crop-search">Search your crops</label>
      <input id="seller-crop-search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search crops" className={inputClassName} />
      <label className="sr-only" htmlFor="seller-crop-category">Filter by category</label>
      <select id="seller-crop-category" value={category} onChange={(event) => setCategory(event.target.value)} className={inputClassName}><option value="ALL">All categories</option>{categories.map((item) => <option key={item}>{item}</option>)}</select>
      <label className="sr-only" htmlFor="seller-crop-availability">Filter by availability</label>
      <select id="seller-crop-availability" value={availability} onChange={(event) => setAvailability(event.target.value)} className={inputClassName}><option value="ALL">All availability</option><option value="ACTIVE">Active</option><option value="PAUSED">Paused</option></select>
    </div>
    {filtered.length === 0 ? <EmptyState title={myCrops.length ? 'No matching crops' : 'No crops listed yet'} description={myCrops.length ? 'Try changing the search or filters.' : 'Create your first listing when you are ready. Only you can manage your local demo crops.'} action={!myCrops.length ? <Link to="/seller/crops/new" className={primaryButtonClassName}>Add a crop</Link> : undefined} /> : <div className="grid gap-3 xl:grid-cols-2">
      {filtered.map((crop) => <article key={crop.id} className="min-w-0 rounded-2xl border border-[#e5eae3] bg-white p-4 shadow-sm sm:p-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0"><p className="text-xs font-bold uppercase tracking-[.1em] text-[#0b6b3a]">{crop.category}</p><h2 className="mt-1 break-words text-lg font-bold text-[#263a2d]">{crop.name}</h2></div>
          <StatusBadge status={crop.availability} />
        </div>
        <p className="mt-3 text-sm text-[#53635a]">{crop.quantity} {crop.unit} · ₹{crop.price} / {crop.unit}</p>
        <p className="mt-1 text-xs text-[#66736b]">{crop.location}{crop.harvestDate ? ` · Harvest ${crop.harvestDate}` : ''}</p>
        <details className="mt-4 rounded-xl bg-[#f7f9f5] px-3 py-2">
          <summary className="cursor-pointer text-xs font-bold text-[#31533d]">View crop details</summary>
          <p className="mt-2 whitespace-pre-wrap text-xs leading-5 text-[#66736b]">{crop.description || 'No description provided.'}</p>
          <p className="mt-2 text-[10px] text-[#87928a]">Listing ID: {crop.id}</p>
        </details>
        <div className="mt-4 flex flex-wrap gap-2">
          <Link to={`/seller/crops/${crop.id}/edit`} className={secondaryButtonClassName}>Edit</Link>
          <button onClick={() => updateCrop(crop.id, { availability: crop.availability === 'ACTIVE' ? 'PAUSED' : 'ACTIVE' })} className={secondaryButtonClassName}>{crop.availability === 'ACTIVE' ? 'Pause availability' : 'Make active'}</button>
          <button onClick={() => removeCrop(crop.id)} className={secondaryButtonClassName}>Remove</button>
        </div>
      </article>)}
    </div>}
  </div>;
}
