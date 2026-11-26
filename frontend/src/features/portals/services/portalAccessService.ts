import type { SellerApplicationRecord, SellerApplicationStatus } from '../types';

export function getSellerStatus(application: SellerApplicationRecord | null): SellerApplicationStatus {
  return application?.status ?? 'NOT_APPLIED';
}
