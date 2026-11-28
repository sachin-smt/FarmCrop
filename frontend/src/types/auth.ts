import type { SellerStatus } from './seller';

export type UserRole = 'customer' | 'farmer' | 'admin';
export interface UserProfile { id: string; email: string | null; name: string | null; username?: string; avatar: string | null; phoneNumber: string | null; phoneVerified: boolean; authProvider: string | null; role: UserRole; sellerStatus: SellerStatus; preferredLanguage: string; createdAt: string | null; updatedAt: string | null; }
