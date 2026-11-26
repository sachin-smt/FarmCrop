import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { Apple, Leaf, LoaderCircle, Phone, ShieldCheck } from 'lucide-react';
import { useAuth, type OAuthProvider } from '../hooks/useAuth';
import { useTranslation } from 'react-i18next';
import { LanguageSelector } from '../components/i18n/LanguageSelector';
import { PasswordLoginForm } from '../components/auth/PasswordLoginForm';

const socialProviders: Array<{ provider: OAuthProvider; label: string; mark: string }> = [
  { provider: 'google', label: 'Google', mark: 'G' },
  { provider: 'facebook', label: 'Facebook', mark: 'f' },
  { provider: 'x', label: 'X', mark: 'X' },
  { provider: 'apple', label: 'Apple ID', mark: '●' },
];

export function Login() {
  const { t } = useTranslation();
  const { signInWithPassword, signInWithOAuth, sendOtp } = useAuth();
  const [phone, setPhone] = useState('');
  const [countryCode, setCountryCode] = useState('+91');
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');

  async function submitOtp(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setNotice('');
    try {
      await sendOtp(`${countryCode}${phone}`);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : t('auth.otpUnavailable'));
    } finally {
      setBusy(false);
    }
  }

  async function startSocialLogin(provider: OAuthProvider) {
    setNotice('');
    try {
      await signInWithOAuth(provider);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : t('auth.socialUnavailable'));
    }
  }

  return <main className="min-h-[calc(100vh-72px)] bg-[#e8f5ec] px-4 py-10">
    <div className="mx-auto grid max-w-5xl overflow-hidden rounded-3xl bg-[#fffdf8] shadow-[0_20px_70px_rgba(7,52,27,.14)] md:grid-cols-[.9fr_1.1fr]">
      <section className="hidden bg-[#064e2a] p-10 text-white md:block">
        <Leaf size={34} className="text-[#f5c65d]" fill="currentColor" />
        <h1 className="serif mt-16 text-5xl font-bold">FarmCrop</h1>
        <p className="mt-5 max-w-sm leading-7 text-[#cde4cf]">{t('hero.description')}</p>
        <div className="mt-14 flex items-center gap-2 text-sm text-[#d9eadc]"><ShieldCheck size={20} className="text-[#f5c65d]" /> Fresh produce, directly from farmers</div>
      </section>
      <section className="p-6 sm:p-10">
        <div className="flex items-center justify-between">
          <Link to="/" className="flex items-center gap-2 font-bold text-[#064e2a]"><Leaf size={22} fill="currentColor" /> FarmCrop</Link>
          <LanguageSelector />
        </div>
        <div className="mx-auto mt-8 max-w-md">
          <h2 className="serif text-3xl font-bold text-[#122018]">{t('auth.welcome')}</h2>
          <p className="mt-2 text-sm text-[#66736b]">{t('auth.tagline')}</p>
          <div className="mt-6">
            <PasswordLoginForm
              onSubmit={signInWithPassword}
              submitLabel={t('auth.login')}
            />
          </div>
          <div className="my-6 flex items-center gap-3 text-xs text-[#9aa69e]"><span className="h-px flex-1 bg-[#e5eae3]" />{t('auth.or')}<span className="h-px flex-1 bg-[#e5eae3]" /></div>
          <div className="flex justify-center gap-3" role="group" aria-label="Social sign-in options">
            {socialProviders.map(({ provider, label, mark }) => <button
              key={provider}
              type="button"
              onClick={() => void startSocialLogin(provider)}
              aria-label={`Continue with ${label}`}
              title={`${label} sign-in`}
              className="grid size-11 place-items-center rounded-full border border-[#dfe7df] bg-white text-lg font-bold text-[#31533d] transition hover:border-[#0b6b3a] hover:bg-[#f5f8f4]"
            >
              {provider === 'apple' ? <Apple size={19} className="text-black" fill="currentColor" /> : <span className={`${provider === 'facebook' ? 'text-[#1877f2]' : ''} ${provider === 'google' ? 'bg-gradient-to-r from-[#4285f4] via-[#34a853] to-[#ea4335] bg-clip-text text-transparent' : ''}`}>{mark}</span>}
            </button>)}
          </div>
          <p className="mt-3 text-center text-xs text-[#87928a]">{t('auth.socialUnavailable')}</p>
          <div className="my-6 flex items-center gap-3 text-xs text-[#9aa69e]"><span className="h-px flex-1 bg-[#e5eae3]" />{t('auth.or')}<span className="h-px flex-1 bg-[#e5eae3]" /></div>
          <form onSubmit={(event) => void submitOtp(event)}>
            <label htmlFor="mobile-number" className="text-sm font-semibold text-[#31533d]">{t('auth.mobile')}</label>
            <span className="mt-2 flex items-center gap-2 rounded-xl border border-[#dfe7df] bg-white px-3 py-3 focus-within:border-[#0b6b3a] focus-within:ring-2 focus-within:ring-[#0b6b3a]/10">
              <Phone size={17} className="shrink-0 text-[#0b6b3a]" />
              <label className="sr-only" htmlFor="country-code">Country code</label>
              <select id="country-code" value={countryCode} onChange={(event) => setCountryCode(event.target.value)} className="w-16 shrink-0 bg-transparent text-sm outline-none">
                <option value="+91">+91</option>
                <option value="+1">+1</option>
                <option value="+44">+44</option>
              </select>
              <input
                id="mobile-number"
                required
                value={phone}
                onChange={(event) => { setPhone(event.target.value.replace(/\D/g, '').slice(0, 15)); setNotice(''); }}
                type="tel"
                inputMode="tel"
                autoComplete="tel-national"
                placeholder="Enter mobile number"
                className="min-w-0 flex-1 outline-none"
              />
            </span>
            <button disabled={busy || phone.length < 10} type="submit" className="mt-3 flex w-full items-center justify-center gap-2 rounded-xl bg-[#0b6b3a] py-3.5 text-sm font-bold text-white disabled:cursor-not-allowed disabled:opacity-50">
              {busy && <LoaderCircle size={17} className="animate-spin" />}{t('auth.sendOtp')}
            </button>
          </form>
          {notice && <p role="status" className="mt-4 rounded-xl border border-[#f1e4be] bg-[#fffaf0] p-3 text-xs leading-5 text-[#6d5c31]">{notice}</p>}
          <p className="mt-3 text-center text-xs text-[#87928a]">{t('auth.otpUnavailable')}</p>
        </div>
      </section>
    </div>
  </main>;
}
