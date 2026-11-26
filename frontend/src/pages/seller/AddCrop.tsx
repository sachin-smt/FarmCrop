import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ImagePlus } from 'lucide-react';
import { usePortal } from '../../features/portals/PortalContext';
import type { SellerCropInput } from '../../features/portals/types';
import { PageHeader, inputClassName, primaryButtonClassName, secondaryButtonClassName } from '../../features/portals/components/PortalUI';

const emptyCrop: SellerCropInput = {
  name: '',
  category: '',
  description: '',
  quantity: 0,
  unit: 'kg',
  price: 0,
  harvestDate: '',
  location: '',
};

const categories = ['Grains', 'Vegetables', 'Fruits', 'Pulses', 'Oilseeds', 'Spices', 'Millets', 'Dairy', 'Organic', 'Other'];
const units = ['kg', 'quintal', 'tonne', 'piece', 'litre', 'dozen'];

export function AddCrop() {
  const { id } = useParams();
  const { crops, createCrop, updateCrop } = usePortal();
  const existing = id ? crops.find((crop) => crop.id === id) : undefined;
  const [form, setForm] = useState<SellerCropInput>(existing ? {
    name: existing.name,
    category: existing.category,
    description: existing.description,
    quantity: existing.quantity,
    unit: existing.unit,
    price: existing.price,
    harvestDate: existing.harvestDate,
    location: existing.location,
  } : emptyCrop);
  const [imageUrl, setImageUrl] = useState('');
  const [error, setError] = useState('');
  const navigate = useNavigate();

  useEffect(() => () => {
    if (imageUrl) URL.revokeObjectURL(imageUrl);
  }, [imageUrl]);

  function handleImage(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) {
      setImageUrl('');
      return;
    }
    if (imageUrl) URL.revokeObjectURL(imageUrl);
    setImageUrl(URL.createObjectURL(file));
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    try {
      if (id && existing) updateCrop(id, form);
      else createCrop(form);
      navigate('/seller/crops', { replace: true });
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Unable to save the crop listing.');
    }
  }

  return <div>
    <PageHeader eyebrow="Crop management" title={id ? 'Edit crop listing' : 'Add a new crop'} description="Share clear information with buyers. Listings are stored only in this browser session." />
    <div className="mb-5 rounded-xl border border-[#f1e4be] bg-[#fffaf0] p-3 text-xs leading-5 text-[#6d5c31]">Demo listing only. Image selection is for local preview and is not uploaded or persisted.</div>
    {!existing && id ? <div role="alert" className="rounded-xl bg-[#fff1ed] p-4 text-sm text-[#a94837]">Crop listing not found. <button onClick={() => navigate('/seller/crops')} className="font-bold underline">Return to My Crops</button></div> : <form onSubmit={submit} className="max-w-3xl space-y-5">
      <section className="grid gap-4 rounded-2xl border border-[#e5eae3] bg-white p-4 shadow-sm sm:grid-cols-2 sm:p-6">
        <label className="text-xs font-bold text-[#31533d]">Crop name <span className="text-[#d95a40]">*</span>
          <input required value={form.name} onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))} className={`${inputClassName} mt-2`} />
        </label>
        <label className="text-xs font-bold text-[#31533d]">Category <span className="text-[#d95a40]">*</span>
          <select required value={form.category} onChange={(event) => setForm((current) => ({ ...current, category: event.target.value }))} className={`${inputClassName} mt-2`}><option value="">Choose category</option>{categories.map((item) => <option key={item}>{item}</option>)}</select>
        </label>
        <label className="text-xs font-bold text-[#31533d]">Available quantity <span className="text-[#d95a40]">*</span>
          <input required type="number" min="0.01" step="0.01" value={form.quantity || ''} onChange={(event) => setForm((current) => ({ ...current, quantity: Number(event.target.value) }))} className={`${inputClassName} mt-2`} />
        </label>
        <label className="text-xs font-bold text-[#31533d]">Measurement unit <span className="text-[#d95a40]">*</span>
          <select required value={form.unit} onChange={(event) => setForm((current) => ({ ...current, unit: event.target.value }))} className={`${inputClassName} mt-2`}>{units.map((unit) => <option key={unit}>{unit}</option>)}</select>
        </label>
        <label className="text-xs font-bold text-[#31533d]">Price per unit (₹) <span className="text-[#d95a40]">*</span>
          <input required type="number" min="0.01" step="0.01" value={form.price || ''} onChange={(event) => setForm((current) => ({ ...current, price: Number(event.target.value) }))} className={`${inputClassName} mt-2`} />
        </label>
        <label className="text-xs font-bold text-[#31533d]">Expected harvest date
          <input type="date" value={form.harvestDate} onChange={(event) => setForm((current) => ({ ...current, harvestDate: event.target.value }))} className={`${inputClassName} mt-2`} />
        </label>
        <label className="text-xs font-bold text-[#31533d] sm:col-span-2">Farm / pickup location <span className="text-[#d95a40]">*</span>
          <input required value={form.location} onChange={(event) => setForm((current) => ({ ...current, location: event.target.value }))} className={`${inputClassName} mt-2`} />
        </label>
        <label className="text-xs font-bold text-[#31533d] sm:col-span-2">Crop description <span className="text-[#d95a40]">*</span>
          <textarea required rows={4} value={form.description} onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))} className={`${inputClassName} mt-2 resize-y`} />
        </label>
        <label className="block rounded-xl border border-dashed border-[#b9cbb9] bg-[#f7f9f5] p-4 text-xs font-bold text-[#31533d] sm:col-span-2">Crop image (optional)
          <span className="mt-2 flex items-center gap-2 text-xs font-normal text-[#66736b]"><ImagePlus size={18} />Select an image for preview</span>
          <input type="file" accept="image/*" onChange={handleImage} className="mt-3 block w-full text-xs text-[#66736b] file:mr-3 file:rounded-lg file:border-0 file:bg-[#e8f5ec] file:px-3 file:py-2 file:text-xs file:font-bold file:text-[#0b6b3a]" />
          {imageUrl && <img src={imageUrl} alt="Selected crop preview" className="mt-3 max-h-56 max-w-full rounded-xl object-cover" />}
        </label>
      </section>
      {error && <p role="alert" className="rounded-xl bg-[#fff1ed] p-3 text-sm text-[#a94837]">{error}</p>}
      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <button type="button" onClick={() => navigate('/seller/crops')} className={secondaryButtonClassName}>Cancel</button>
        <button type="submit" className={primaryButtonClassName}>{id ? 'Save changes' : 'Save crop listing'}</button>
      </div>
    </form>}
  </div>;
}
