import { useState } from 'react';
import type { CartItem, CropCategory } from '../types';
import { AIChatButton } from '../features/chatbot/components/AIChatButton';
import { AIChatPanel } from '../features/chatbot/components/AIChatPanel';
import { CategoryBar, CommunityStats, FarmerCTA, Hero, ProductSection, TrustFeatures, TrustSection } from '../components/home/HomeSections';

interface HomeProps { cart: CartItem[]; addToCart: (item: CartItem) => void; }
export function Home({ addToCart }: HomeProps) { const [category, setCategory] = useState<CropCategory>('All Crops'); const [assistantOpen, setAssistantOpen] = useState(false); return <main><Hero /><TrustFeatures /><CategoryBar active={category} onChange={setCategory} /><ProductSection category={category} onAdd={addToCart} /><FarmerCTA /><CommunityStats /><TrustSection /><AIChatButton open={assistantOpen} onClick={() => setAssistantOpen(true)} />{assistantOpen && <AIChatPanel onClose={() => setAssistantOpen(false)} />}</main>; }
