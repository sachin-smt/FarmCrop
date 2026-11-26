import type {
  ReviewAction,
  ReviewRecord,
  SellerApplicationInput,
  SellerApplicationRecord,
  SellerApplicationStatus,
  SellerCrop,
  SellerCropInput,
} from '../types';

export interface PortalStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

const APPLICATIONS_KEY = 'farmcrop.demo.seller-applications';
const CROPS_KEY = 'farmcrop.demo.seller-crops';

function readList<T>(storage: PortalStorage, key: string): T[] {
  const rawValue = storage.getItem(key);
  if (rawValue === null) return [];
  const value: unknown = JSON.parse(rawValue);
  if (!Array.isArray(value)) throw new Error(`Saved demo data for "${key}" is invalid.`);
  return value as T[];
}

function makeId(prefix: string): string {
  return `${prefix}-${crypto.randomUUID()}`;
}

function makeReview(action: ReviewRecord['action'], note = ''): ReviewRecord {
  return { action, note, createdAt: new Date().toISOString() };
}

const reviewableStatuses: SellerApplicationStatus[] = ['PENDING', 'UNDER_REVIEW'];

export function createPortalDataService(storage: PortalStorage) {
  const getApplications = () => readList<SellerApplicationRecord>(storage, APPLICATIONS_KEY);
  const saveApplications = (applications: SellerApplicationRecord[]) => {
    storage.setItem(APPLICATIONS_KEY, JSON.stringify(applications));
  };
  const getCrops = () => readList<SellerCrop>(storage, CROPS_KEY);
  const saveCrops = (crops: SellerCrop[]) => storage.setItem(CROPS_KEY, JSON.stringify(crops));

  function getLatestApplication(sellerId: string): SellerApplicationRecord | null {
    return getApplications()
      .filter((application) => application.sellerId === sellerId)
      .sort((left, right) => right.submittedAt.localeCompare(left.submittedAt))[0] ?? null;
  }

  function submitApplication(sellerId: string, input: SellerApplicationInput): SellerApplicationRecord {
    const requiredFields = [
      input.fullName,
      input.email,
      input.phone,
      input.farmName,
      input.state,
      input.district,
      input.village,
      input.address,
      input.pinCode,
      input.description,
      input.experience,
      input.cropsGrown,
    ];
    if (requiredFields.some((value) => !value.trim())) throw new Error('Complete all required application fields.');
    if (!input.documents.identity || !input.documents.address || !input.documents.farmAuthorization) {
      throw new Error('Select all required verification document samples.');
    }

    const applications = getApplications();
    const latest = applications
      .filter((application) => application.sellerId === sellerId)
      .sort((left, right) => right.submittedAt.localeCompare(left.submittedAt))[0];

    if (latest && !['REJECTED', 'RESUBMISSION_REQUIRED'].includes(latest.status)) {
      throw new Error('An application is already awaiting review or has been approved.');
    }

    const now = new Date().toISOString();
    if (latest?.status === 'RESUBMISSION_REQUIRED') {
      const updated: SellerApplicationRecord = {
        ...latest,
        ...input,
        status: 'PENDING',
        submittedAt: now,
        updatedAt: now,
        adminRemarks: '',
        missingInformation: '',
        history: [...latest.history, makeReview('RESUBMITTED')],
      };
      saveApplications(applications.map((application) => application.id === latest.id ? updated : application));
      return updated;
    }

    const created: SellerApplicationRecord = {
      ...input,
      id: makeId('APP'),
      sellerId,
      status: 'PENDING',
      submittedAt: now,
      updatedAt: now,
      adminRemarks: '',
      missingInformation: '',
      history: [makeReview('RESUBMITTED', 'Application submitted')],
    };
    saveApplications([...applications, created]);
    return created;
  }

  function reviewApplication(id: string, action: ReviewAction, note = ''): SellerApplicationRecord {
    const applications = getApplications();
    const current = applications.find((application) => application.id === id);
    if (!current) throw new Error('Seller application was not found.');
    if (!reviewableStatuses.includes(current.status)) throw new Error('This application is no longer available for review.');
    if ((action === 'REJECTED' || action === 'RESUBMISSION_REQUIRED') && !note.trim()) {
      throw new Error(action === 'REJECTED' ? 'A rejection reason is required.' : 'Missing information must be specified.');
    }

    const updated: SellerApplicationRecord = {
      ...current,
      status: action,
      adminRemarks: action === 'REJECTED' ? note.trim() : '',
      missingInformation: action === 'RESUBMISSION_REQUIRED' ? note.trim() : '',
      updatedAt: new Date().toISOString(),
      history: [...current.history, makeReview(action, note.trim())],
    };
    saveApplications(applications.map((application) => application.id === id ? updated : application));
    return updated;
  }

  function setSellerSuspended(sellerId: string, suspended: boolean): SellerApplicationRecord {
    const applications = getApplications();
    const current = applications
      .filter((application) => application.sellerId === sellerId)
      .sort((left, right) => right.submittedAt.localeCompare(left.submittedAt))[0];
    if (!current || (suspended && current.status !== 'APPROVED') || (!suspended && current.status !== 'SUSPENDED')) {
      throw new Error('This seller account cannot be changed in its current state.');
    }

    const updated: SellerApplicationRecord = {
      ...current,
      status: suspended ? 'SUSPENDED' : 'APPROVED',
      updatedAt: new Date().toISOString(),
      history: [...current.history, makeReview(suspended ? 'SUSPENDED' : 'REACTIVATED')],
    };
    saveApplications(applications.map((application) => application.id === current.id ? updated : application));
    return updated;
  }

  function createCrop(sellerId: string, input: SellerCropInput): SellerCrop {
    const crops = getCrops();
    const created: SellerCrop = {
      ...input,
      id: makeId('CROP'),
      sellerId,
      availability: 'ACTIVE',
      createdAt: new Date().toISOString(),
    };
    saveCrops([...crops, created]);
    return created;
  }

  function updateCrop(id: string, update: Partial<SellerCropInput & { availability: SellerCrop['availability'] }>): SellerCrop {
    const crops = getCrops();
    const current = crops.find((crop) => crop.id === id);
    if (!current) throw new Error('Crop listing was not found.');
    const updated = { ...current, ...update };
    saveCrops(crops.map((crop) => crop.id === id ? updated : crop));
    return updated;
  }

  function deleteCrop(id: string): void {
    const crops = getCrops();
    if (!crops.some((crop) => crop.id === id)) throw new Error('Crop listing was not found.');
    saveCrops(crops.filter((crop) => crop.id !== id));
  }

  return {
    getApplications,
    getCrops,
    getLatestApplication,
    submitApplication,
    reviewApplication,
    setSellerSuspended,
    createCrop,
    updateCrop,
    deleteCrop,
  };
}
