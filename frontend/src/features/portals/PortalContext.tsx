import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';
import { createPortalDataService } from './services/portalDataService';
import type {
  ReviewAction,
  SellerApplicationInput,
  SellerApplicationRecord,
  SellerCrop,
  SellerCropInput,
} from './types';

interface PortalContextValue {
  applications: SellerApplicationRecord[];
  crops: SellerCrop[];
  submitApplication: (input: SellerApplicationInput) => SellerApplicationRecord;
  reviewApplication: (id: string, action: ReviewAction, note?: string) => SellerApplicationRecord;
  setSellerSuspended: (sellerId: string, suspended: boolean) => SellerApplicationRecord;
  createCrop: (input: SellerCropInput) => SellerCrop;
  updateCrop: (id: string, update: Partial<SellerCropInput & { availability: SellerCrop['availability'] }>) => SellerCrop;
  deleteCrop: (id: string) => void;
  adminUpdateCrop: (id: string, update: Partial<SellerCropInput & { availability: SellerCrop['availability'] }>) => SellerCrop;
  latestApplication: SellerApplicationRecord | null;
}

const PortalContext = createContext<PortalContextValue | null>(null);

export function PortalProvider({ children }: { children: ReactNode }) {
  const storage = window.sessionStorage;
  const data = useMemo(() => createPortalDataService(storage), [storage]);
  const [applications, setApplications] = useState(() => data.getApplications());
  const [crops, setCrops] = useState(() => data.getCrops());

  function submitApplication(input: SellerApplicationInput) {
    const sellerId = latestApplication?.sellerId ?? 'frontend-preview';
    const application = data.submitApplication(sellerId, input);
    setApplications(data.getApplications());
    return application;
  }

  function reviewApplication(id: string, action: ReviewAction, note = '') {
    const application = data.reviewApplication(id, action, note);
    setApplications(data.getApplications());
    return application;
  }

  function setSellerSuspended(sellerId: string, suspended: boolean) {
    const application = data.setSellerSuspended(sellerId, suspended);
    setApplications(data.getApplications());
    return application;
  }

  function createCrop(input: SellerCropInput) {
    const sellerId = latestApplication?.sellerId ?? 'frontend-preview';
    const crop = data.createCrop(sellerId, input);
    setCrops(data.getCrops());
    return crop;
  }

  function updateCrop(id: string, update: Partial<SellerCropInput & { availability: SellerCrop['availability'] }>) {
    const current = crops.find((crop) => crop.id === id);
    if (!current) throw new Error('Crop listing was not found.');
    const crop = data.updateCrop(id, update);
    setCrops(data.getCrops());
    return crop;
  }

  function deleteCrop(id: string) {
    const current = crops.find((crop) => crop.id === id);
    if (!current) throw new Error('Crop listing was not found.');
    data.deleteCrop(id);
    setCrops(data.getCrops());
  }

  function adminUpdateCrop(id: string, update: Partial<SellerCropInput & { availability: SellerCrop['availability'] }>) {
    const crop = data.updateCrop(id, update);
    setCrops(data.getCrops());
    return crop;
  }

  const latestApplication = useMemo(
    () => [...applications].sort((left, right) => right.submittedAt.localeCompare(left.submittedAt))[0] ?? null,
    [applications],
  );

  const value: PortalContextValue = {
    applications,
    crops,
    submitApplication,
    reviewApplication,
    setSellerSuspended,
    createCrop,
    updateCrop,
    deleteCrop,
    adminUpdateCrop,
    latestApplication,
  };

  return <PortalContext.Provider value={value}>{children}</PortalContext.Provider>;
}

export function usePortal() {
  const value = useContext(PortalContext);
  if (!value) throw new Error('usePortal must be used inside PortalProvider');
  return value;
}
