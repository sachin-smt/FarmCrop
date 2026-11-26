import type { Crop, CropCategory } from './types';

export const categories: { label: CropCategory; icon: string }[] = [
  { label: 'All Crops', icon: '▦' }, { label: 'Grains', icon: '🌾' }, { label: 'Vegetables', icon: '🍅' },
  { label: 'Fruits', icon: '🥭' }, { label: 'Pulses', icon: '🫘' }, { label: 'Oilseeds', icon: '🌻' },
  { label: 'Spices', icon: '🌶' }, { label: 'Millets', icon: '🌾' }, { label: 'Dairy', icon: '🥛' }, { label: 'Organic', icon: '🌿' },
];

const images = {
  wheat: 'https://images.unsplash.com/photo-1574323347407-f5e1ad6d020b?auto=format&fit=crop&w=700&q=85',
  tomato: 'https://images.unsplash.com/photo-1546094096-0df4bcaaa337?auto=format&fit=crop&w=700&q=85',
  onion: 'https://images.unsplash.com/photo-1618512496248-a07fe83aa8cb?auto=format&fit=crop&w=700&q=85',
  potato: 'https://images.unsplash.com/photo-1518977676601-b53f82aba655?auto=format&fit=crop&w=700&q=85',
  chilli: 'https://images.unsplash.com/photo-1588252303782-cb80119abd6d?auto=format&fit=crop&w=700&q=85',
  hero: 'https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=1800&q=90',
};

export const heroImage = images.hero;

export const crops: Crop[] = [
  { id: 'wheat', name: 'Wheat', category: 'Grains', price: 25, unit: 'kg', image: images.wheat, location: 'Patna, Bihar', rating: 4.8, reviews: 120, badges: ['Fresh', 'Locally Grown'] },
  { id: 'tomato', name: 'Tomato', category: 'Vegetables', price: 30, unit: 'kg', image: images.tomato, location: 'Nashik, Maharashtra', rating: 4.6, reviews: 98, badges: ['Organic', 'Fresh'] },
  { id: 'onion', name: 'Onion', category: 'Vegetables', price: 28, unit: 'kg', image: images.onion, location: 'Indore, Madhya Pradesh', rating: 4.7, reviews: 86, badges: ['Fresh'] },
  { id: 'potato', name: 'Potato', category: 'Vegetables', price: 20, unit: 'kg', image: images.potato, location: 'Agra, Uttar Pradesh', rating: 4.7, reviews: 86, badges: ['Fresh'] },
  { id: 'chilli', name: 'Green Chilli', category: 'Spices', price: 40, unit: 'kg', image: images.chilli, location: 'Guntur, Andhra Pradesh', rating: 4.6, reviews: 76, badges: ['Organic'] },
];

export const quickQuestions = ['मेरी फसल कैसे बढ़े?', 'आज का मंडी भाव क्या है?', 'मेरी फसल में बीमारी है', 'सरकारी योजना बताओ', 'मौसम कैसा रहेगा?'];
