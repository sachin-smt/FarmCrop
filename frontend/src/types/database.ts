export type DatabaseCropStatus = 'draft' | 'active' | 'paused' | 'archived' | 'rejected';
export type DatabaseCropUnit = 'kg' | 'gram' | 'quintal' | 'ton' | 'litre' | 'piece' | 'bundle';

export interface DatabaseCrop {
  id: string;
  farmer_id: string;
  category_id: string;
  name: string;
  slug: string;
  description: string | null;
  variety: string | null;
  unit: DatabaseCropUnit;
  price: number;
  image_url: string | null;
  farming_practice: string | null;
  status: DatabaseCropStatus;
  created_at: string;
  updated_at: string;
  published_at: string | null;
}

export interface DatabaseCropCategory { id: string; name: string; slug: string; description: string | null; image_url: string | null; is_active: boolean; }
