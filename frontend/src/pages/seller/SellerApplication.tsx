import { useState, type ChangeEvent, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { FileUp, ShieldCheck } from 'lucide-react';
import { usePortal } from '../../features/portals/PortalContext';
import type { SellerApplicationInput } from '../../features/portals/types';
import { PageHeader, inputClassName, primaryButtonClassName, secondaryButtonClassName } from '../../features/portals/components/PortalUI';

const emptyApplication: SellerApplicationInput = {
  fullName: '',
  email: '',
  phone: '',
  farmName: '',
  state: '',
  district: '',
  village: '',
  address: '',
  pinCode: '',
  description: '',
  experience: '',
  cropsGrown: '',
  documents: { identity: false, address: false, farmAuthorization: false },
};

const documentFields = [
  { key: 'identity', label: 'Identity document' },
  { key: 'address', label: 'Address proof' },
  { key: 'farmAuthorization', label: 'Farm ownership / authorization' },
] as const;

type SellerTextField = keyof Omit<SellerApplicationInput, 'documents'>;

export function SellerApplication() {
  const { latestApplication, submitApplication } = usePortal();
  const isResubmission = latestApplication?.status === 'RESUBMISSION_REQUIRED';
  const [form, setForm] = useState<SellerApplicationInput>(() => isResubmission && latestApplication
    ? { ...latestApplication, documents: { identity: false, address: false, farmAuthorization: false } }
    : latestApplication?.status === 'REJECTED' && latestApplication
      ? {
        ...emptyApplication,
        fullName: latestApplication.fullName,
        email: latestApplication.email,
        phone: latestApplication.phone,
        farmName: latestApplication.farmName,
        state: latestApplication.state,
        district: latestApplication.district,
        village: latestApplication.village,
        address: latestApplication.address,
        pinCode: latestApplication.pinCode,
        description: latestApplication.description,
        experience: latestApplication.experience,
        cropsGrown: latestApplication.cropsGrown,
      }
      : emptyApplication);
  const [selectedFiles, setSelectedFiles] = useState<Record<(typeof documentFields)[number]['key'], string>>({
    identity: '',
    address: '',
    farmAuthorization: '',
  });
  const [error, setError] = useState('');
  const navigate = useNavigate();

  function updateField(field: SellerTextField, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  function selectDocument(key: (typeof documentFields)[number]['key'], event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    setSelectedFiles((current) => ({ ...current, [key]: file?.name ?? '' }));
    setForm((current) => ({ ...current, documents: { ...current.documents, [key]: Boolean(file) } }));
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    if (documentFields.some(({ key }) => !form.documents[key])) {
      setError('Select each required document. Files stay in this browser and are not uploaded.');
      return;
    }
    try {
      submitApplication(form);
      navigate('/seller/application-status', { replace: true });
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : 'Unable to submit the application.');
    }
  }

  const textFields: Array<{ key: SellerTextField; label: string; type?: string; placeholder?: string }> = [
    { key: 'fullName', label: 'Full name' },
    { key: 'email', label: 'Email address', type: 'email' },
    { key: 'phone', label: 'Phone number', type: 'tel' },
    { key: 'farmName', label: 'Farm / business name' },
    { key: 'state', label: 'State' },
    { key: 'district', label: 'District' },
    { key: 'village', label: 'Village' },
    { key: 'pinCode', label: 'PIN code', placeholder: '6-digit PIN code' },
  ];

  return <div>
    <PageHeader eyebrow="Seller verification" title={isResubmission ? 'Update your application' : 'Apply to sell on FarmCrop'} description="Tell us about your farm. Application review details are available on the status page; seller tools remain accessible during frontend development." />
    <div className="mb-5 flex gap-3 rounded-2xl border border-[#f1e4be] bg-[#fffaf0] p-4 text-xs leading-5 text-[#6d5c31]">
      <ShieldCheck size={18} className="mt-0.5 shrink-0" />
      <p>Local demo form only. Do not enter real personal data or select actual identity documents. File contents stay in memory and are never uploaded or saved.</p>
    </div>
    {isResubmission && latestApplication?.missingInformation && <div className="mb-5 rounded-xl border border-[#f1e4be] bg-white p-4 text-sm leading-6 text-[#6d5c31]"><b>Requested updates:</b> {latestApplication.missingInformation}</div>}
    {latestApplication?.status === 'REJECTED' && latestApplication.adminRemarks && <div className="mb-5 rounded-xl border border-[#f4d0c8] bg-[#fff7f4] p-4 text-sm leading-6 text-[#8c4135]"><b>Previous review:</b> {latestApplication.adminRemarks}</div>}
    <form onSubmit={submit} className="space-y-5">
      <section className="rounded-2xl border border-[#e5eae3] bg-white p-4 shadow-sm sm:p-6">
        <h2 className="text-base font-bold text-[#263a2d]">Your details</h2>
        <p className="mt-1 text-xs text-[#87928a]">Fields marked required must be completed.</p>
        <div className="mt-5 grid gap-4 sm:grid-cols-2">
          {textFields.map(({ key, label, type, placeholder }) => <label key={key} className="block text-xs font-bold text-[#31533d]">{label} <span className="text-[#d95a40]">*</span>
            <input required type={type ?? 'text'} value={form[key]} onChange={(event) => updateField(key, event.target.value)} placeholder={placeholder} className={`${inputClassName} mt-2`} />
          </label>)}
          <label className="block text-xs font-bold text-[#31533d] sm:col-span-2">Full farm address <span className="text-[#d95a40]">*</span>
            <textarea required rows={3} value={form.address} onChange={(event) => updateField('address', event.target.value)} className={`${inputClassName} mt-2 resize-y`} />
          </label>
        </div>
      </section>
      <section className="rounded-2xl border border-[#e5eae3] bg-white p-4 shadow-sm sm:p-6">
        <h2 className="text-base font-bold text-[#263a2d]">Farm information</h2>
        <div className="mt-5 grid gap-4 sm:grid-cols-2">
          <label className="block text-xs font-bold text-[#31533d]">Farming experience <span className="text-[#d95a40]">*</span>
            <input required value={form.experience} onChange={(event) => updateField('experience', event.target.value)} placeholder="e.g. 5 years" className={`${inputClassName} mt-2`} />
          </label>
          <label className="block text-xs font-bold text-[#31533d]">Types of crops grown <span className="text-[#d95a40]">*</span>
            <input required value={form.cropsGrown} onChange={(event) => updateField('cropsGrown', event.target.value)} placeholder="e.g. wheat, vegetables" className={`${inputClassName} mt-2`} />
          </label>
          <label className="block text-xs font-bold text-[#31533d] sm:col-span-2">About your farm <span className="text-[#d95a40]">*</span>
            <textarea required rows={4} value={form.description} onChange={(event) => updateField('description', event.target.value)} placeholder="Share a little about your farm and the crops you grow." className={`${inputClassName} mt-2 resize-y`} />
          </label>
        </div>
      </section>
      <section className="rounded-2xl border border-[#e5eae3] bg-white p-4 shadow-sm sm:p-6">
        <h2 className="text-base font-bold text-[#263a2d]">Verification documents</h2>
        <p className="mt-1 text-xs leading-5 text-[#66736b]">Select a local sample file to preview its name. File data is not retained after you leave this page.</p>
        <div className="mt-4 grid gap-3 md:grid-cols-3">
          {documentFields.map(({ key, label }) => <label key={key} className="flex min-h-32 cursor-pointer flex-col justify-between rounded-xl border border-dashed border-[#b9cbb9] bg-[#f7f9f5] p-4 text-xs">
            <span className="font-bold text-[#31533d]">{label} <span className="text-[#d95a40]">*</span></span>
            <span className="mt-2 flex items-center gap-2 text-[#66736b]"><FileUp size={17} />{selectedFiles[key] || 'Choose PDF or image'}</span>
            <input required={!form.documents[key]} type="file" accept=".pdf,.png,.jpg,.jpeg,image/*" onChange={(event) => selectDocument(key, event)} className="mt-3 block w-full text-[10px] text-[#66736b] file:mr-2 file:rounded-lg file:border-0 file:bg-[#e8f5ec] file:px-3 file:py-2 file:text-[10px] file:font-bold file:text-[#0b6b3a]" />
          </label>)}
        </div>
      </section>
      {error && <p role="alert" className="rounded-xl bg-[#fff1ed] p-3 text-sm text-[#a94837]">{error}</p>}
      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <button type="button" onClick={() => navigate('/seller/application-status')} className={secondaryButtonClassName}>Cancel</button>
        <button type="submit" className={primaryButtonClassName}>{isResubmission ? 'Resubmit application' : 'Submit application'}</button>
      </div>
    </form>
  </div>;
}
