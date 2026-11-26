import { useState } from 'react';
import { Bot } from 'lucide-react';
import { AIChatPanel } from '../../features/chatbot/components/AIChatPanel';
import { PageHeader } from '../../features/portals/components/PortalUI';

export function SellerAssistant() {
  const [open, setOpen] = useState(false);
  return <div>
    <PageHeader eyebrow="FarmCrop assistant" title="AI Assistant" description="Ask about crop listing guidance, marketplace navigation, and general farming topics. The response service is still a local demo placeholder." />
    <section className="max-w-2xl rounded-2xl border border-[#e5eae3] bg-white p-5 shadow-sm sm:p-7">
      <span className="grid size-12 place-items-center rounded-2xl bg-[#e8f5ec] text-[#0b6b3a]"><Bot size={23} /></span>
      <h2 className="mt-4 text-lg font-bold text-[#263a2d]">Krishi Saathi</h2>
      <p className="mt-2 text-sm leading-6 text-[#66736b]">The existing multilingual chat interface is available here. It is not connected to an AI provider or backend.</p>
      <button onClick={() => setOpen(true)} className="mt-5 min-h-11 rounded-xl bg-[#0b6b3a] px-4 py-2.5 text-sm font-bold text-white hover:bg-[#064e2a]">Open assistant</button>
    </section>
    {open && <AIChatPanel onClose={() => setOpen(false)} />}
  </div>;
}
