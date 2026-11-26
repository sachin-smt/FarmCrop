export type CropCategory = 'All Crops' | 'Grains' | 'Vegetables' | 'Fruits' | 'Pulses' | 'Oilseeds' | 'Spices' | 'Millets' | 'Dairy' | 'Organic';

export interface Crop {
  id: string;
  name: string;
  category: CropCategory;
  price: number;
  unit: string;
  image: string;
  location: string;
  rating: number;
  reviews: number;
  badges: string[];
}

export interface CartItem extends Crop {
  quantity: number;
}

export interface AIMessage {
  id: string;
  role: 'assistant' | 'user';
  content: string;
}
