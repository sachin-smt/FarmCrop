import { beforeEach, describe, expect, it } from 'vitest';
import { getSellerStatus } from './portalAccessService';
import { createPortalDataService, type PortalStorage } from './portalDataService';
import type { SellerApplicationInput } from '../types';

class MemoryStorage implements PortalStorage {
  private readonly values = new Map<string, string>();

  getItem(key: string) {
    return this.values.get(key) ?? null;
  }

  setItem(key: string, value: string) {
    this.values.set(key, value);
  }

  removeItem(key: string) {
    this.values.delete(key);
  }
}

const sampleApplication: SellerApplicationInput = {
  fullName: 'Demo Farmer',
  email: 'farmer@example.test',
  phone: '0000000000',
  farmName: 'Demo Farm',
  state: 'Demo State',
  district: 'Demo District',
  village: 'Demo Village',
  address: 'Sample address',
  pinCode: '000000',
  description: 'Sample farm description',
  experience: '2 years',
  cropsGrown: 'Wheat',
  documents: { identity: true, address: true, farmAuthorization: true },
};

describe('local seller application workflow', () => {
  let data: ReturnType<typeof createPortalDataService>;

  beforeEach(() => {
    data = createPortalDataService(new MemoryStorage());
  });

  it('starts a new application in pending status', () => {
    const application = data.submitApplication('seller-1', sampleApplication);

    expect(application.status).toBe('PENDING');
    expect(getSellerStatus(application)).toBe('PENDING');
  });

  it('requires an Admin reason for rejection and retains it for the seller', () => {
    const application = data.submitApplication('seller-1', sampleApplication);

    expect(() => data.reviewApplication(application.id, 'REJECTED')).toThrow('A rejection reason is required.');
    const rejected = data.reviewApplication(application.id, 'REJECTED', 'Please provide clearer farm authorization.');

    expect(rejected.status).toBe('REJECTED');
    expect(rejected.adminRemarks).toBe('Please provide clearer farm authorization.');
    expect(getSellerStatus(rejected)).toBe('REJECTED');
  });

  it('approves only after an explicit review decision', () => {
    const application = data.submitApplication('seller-1', sampleApplication);
    const approved = data.reviewApplication(application.id, 'APPROVED');

    expect(approved.status).toBe('APPROVED');
    expect(getSellerStatus(approved)).toBe('APPROVED');
  });

  it('removes marketplace access while suspended and restores it after Admin reactivation', () => {
    const application = data.submitApplication('seller-1', sampleApplication);
    data.reviewApplication(application.id, 'APPROVED');
    const suspended = data.setSellerSuspended('seller-1', true);

    expect(suspended.status).toBe('SUSPENDED');
    expect(getSellerStatus(suspended)).toBe('SUSPENDED');

    const reactivated = data.setSellerSuspended('seller-1', false);
    expect(getSellerStatus(reactivated)).toBe('APPROVED');
  });

  it('keeps resubmission on the existing application and returns it to pending', () => {
    const application = data.submitApplication('seller-1', sampleApplication);
    data.reviewApplication(application.id, 'RESUBMISSION_REQUIRED', 'Upload an updated address proof.');

    const updated = data.submitApplication('seller-1', sampleApplication);

    expect(updated.id).toBe(application.id);
    expect(updated.status).toBe('PENDING');
    expect(updated.missingInformation).toBe('');
    expect(updated.history.some((entry) => entry.action === 'RESUBMITTED')).toBe(true);
  });

  it('rejects missing required fields or document selections at the data boundary', () => {
    expect(() => data.submitApplication('seller-1', { ...sampleApplication, fullName: ' ' })).toThrow('Complete all required application fields.');
    expect(() => data.submitApplication('seller-1', { ...sampleApplication, documents: { ...sampleApplication.documents, identity: false } })).toThrow('Select all required verification document samples.');
  });

  it('allows a crop listing to be created without an application review', () => {
    const crop = data.createCrop('seller-1', {
      name: 'Wheat',
      category: 'Grains',
      description: 'Sample crop',
      quantity: 10,
      unit: 'kg',
      price: 20,
      harvestDate: '',
      location: 'Sample farm',
    });

    expect(data.getCrops()).toContainEqual(crop);
  });
});
