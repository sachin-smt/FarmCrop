import { Globe2 } from 'lucide-react';
import { useLanguage } from '../../hooks/useLanguage';

export function LanguageSelector() { const { currentLanguage, availableLanguages, setLanguage } = useLanguage(); return <label className="flex items-center gap-2 rounded-full border border-[#e1e5df] bg-white px-3 py-2 text-xs font-semibold text-[#31533d]"><Globe2 size={15} className="text-[#0b6b3a]" /><span className="sr-only">Language</span><select value={currentLanguage.code} onChange={(event) => void setLanguage(event.target.value)} className="max-w-[110px] bg-transparent outline-none" aria-label="Choose language">{availableLanguages.map((language) => <option key={language.code} value={language.code}>{language.nativeName}</option>)}</select></label>; }
