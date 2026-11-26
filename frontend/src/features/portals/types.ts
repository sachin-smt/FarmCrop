export type PortalRole = 'admin' | 'seller';

export type SellerApplicationStatus =
  | 'NOT_APPLIED'
  | 'PENDING'
  | 'UNDER_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'RESUBMISSION_REQUIRED'
  | 'SUSPENDED';

export type ReviewAction = 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED' | 'RESUBMISSION_REQUIRED';

export interface SellerApplicationInput {
  fullName: string;
  email: string;
  phone: string;
  farmName: string;
  state: string;
  district: string;
  village: string;
  address: string;
  pinCode: string;
  description: string;
  experience: string;
  cropsGrown: string;
  documents: {
    identity: boolean;
    address: boolean;
    farmAuthorization: boolean;
  };
}

export interface ReviewRecord {
  action: ReviewAction | 'RESUBMITTED' | 'SUSPENDED' | 'REACTIVATED';
  note: string;
  createdAt: string;
}

export interface SellerApplicationRecord extends SellerApplicationInput {
  id: string;
  sellerId: string;
  status: Exclude<SellerApplicationStatus, 'NOT_APPLIED'>;
  submittedAt: string;
  updatedAt: string;
  adminRemarks: string;
  missingInformation: string;
  history: ReviewRecord[];
}

export type CropAvailability = 'ACTIVE' | 'PAUSED';

export interface SellerCropInput {
  name: string;
  category: string;
  description: string;
  quantity: number;
  unit: string;
  price: number;
  harvestDate: string;
  location: string;
}

export interface SellerCrop extends SellerCropInput {
  id: string;
  sellerId: string;
  availability: CropAvailability;
  createdAt: string;
}

export type MarketplaceOrderStatus =
  | 'PLACED'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'PROCESSING'
  | 'READY_FOR_DISPATCH'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELLED';
