import { useEffect, useState } from 'react';
import { ArrowLeft, Leaf, LoaderCircle } from 'lucide-react';
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { LanguageSelector } from '../components/i18n/LanguageSelector';
import { defaultLandingPath, OTP_RESEND_COOLDOWN_SECONDS, useAuth } from '../hooks/useAuth';

function formatIndianNumber(e164: string) {
  const national = e164.replace(/^\+91/, '');
  return `+91 ${national.slice(0, 5)} ${national.slice(5)}`;
}

export function VerifyPhone() {
  const { t } = useTranslation();
  const { verifyOtp, verifyPhoneForAccount, sendOtp, error, pendingOtp, isAuthenticated, isLoading } = useAuth();
  const [params] = useSearchParams();
  const location = useLocation();
  const navigate = useNavigate();
  const requestedPath = (location.state as { from?: string } | null)?.from;
  const phone = params.get('phone') ?? '';
  // 'account': a signed-in user verifying their own number (from the profile page); otherwise OTP login.
  const forAccount = params.get('purpose') === 'account';
  const backPath = forAccount ? '/profile' : '/login';
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [sending, setSending] = useState(false);
  const [now, setNow] = useState(() => Date.now());

  // A code is only pending in this tab's memory; after a page reload the user must request a new one.
  const codePending = pendingOtp?.phone === phone;
  const secondsLeft = codePending ? Math.max(0, Math.ceil(OTP_RESEND_COOLDOWN_SECONDS - (now - pendingOtp.sentAt) / 1000)) : 0;

  useEffect(() => {
    if (secondsLeft <= 0) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [secondsLeft]);

  if (!/^\+91[6-9]\d{9}$/.test(phone)) return <Navigate to={backPath} replace />;
  if (forAccount && !isLoading && !isAuthenticated) return <Navigate to="/login" replace />;

  const submit = async () => {
    setBusy(true);
    try {
      if (forAccount) {
        await verifyPhoneForAccount(phone, code);
        navigate('/profile', { replace: true, state: { phoneVerified: true } });
      } else {
        const signedIn = await verifyOtp(phone, code);
        navigate(requestedPath ?? defaultLandingPath(signedIn), { replace: true });
      }
    } catch {
      setCode(''); /* the error is rendered below */
    } finally {
      setBusy(false);
    }
  };

  const resend = async () => {
    setSending(true);
    try {
      await sendOtp(phone);
      setCode('');
      setNow(Date.now());
    } catch {
      /* the error is rendered below */
    } finally {
      setSending(false);
    }
  };

  return <main className="min-h-[calc(100vh-72px)] bg-[#e8f5ec] px-4 py-10">
    <div className="mx-auto max-w-md rounded-3xl bg-[#fffdf8] p-6 shadow-[0_20px_70px_rgba(7,52,27,.14)] sm:p-10">
      <div className="flex items-center justify-between"><Link to="/" className="flex items-center gap-2 font-bold text-[#064e2a]"><Leaf size={22} fill="currentColor" /> FarmCrop</Link><LanguageSelector /></div>
      <div className="mt-12">
        <Link to={backPath} state={forAccount ? undefined : { from: requestedPath }} className="mb-8 inline-flex items-center gap-2 text-xs font-bold text-[#0b6b3a]"><ArrowLeft size={15} /> {t('auth.change')}</Link>
        <h1 className="serif text-3xl font-bold text-[#122018]">{t('auth.verifyTitle')}</h1>
        <p className="mt-3 text-sm leading-6 text-[#66736b]">{codePending ? <>{t('auth.verifyDescription')} {t('auth.otpSentTo')} <b className="whitespace-nowrap">{formatIndianNumber(phone)}</b></> : <b className="whitespace-nowrap">{formatIndianNumber(phone)}</b>}</p>
        <form onSubmit={(event) => { event.preventDefault(); void submit(); }}>
          <label className="mt-8 block text-sm font-semibold text-[#31533d]">OTP
            <input value={code} onChange={(event) => setCode(event.target.value.replace(/\D/g, '').slice(0, 6))} disabled={!codePending || busy} inputMode="numeric" autoComplete="one-time-code" autoFocus className="mt-2 w-full rounded-xl border border-[#dfe7df] bg-white px-4 py-4 text-center text-2xl tracking-[.5em] outline-none focus:border-[#0b6b3a] disabled:bg-[#f5f8f4]" placeholder="------" aria-label="6 digit verification code" />
          </label>
          <button type="submit" disabled={busy || !codePending || code.length !== 6} className="mt-5 flex w-full items-center justify-center gap-2 rounded-xl bg-[#0b6b3a] py-3.5 text-sm font-bold text-white disabled:opacity-50">{busy && <LoaderCircle size={17} className="animate-spin" />}{busy ? t('auth.verifying') : t('auth.verify')}</button>
        </form>
        <button type="button" onClick={() => void resend()} disabled={sending || secondsLeft > 0} className="mt-5 flex w-full items-center justify-center gap-2 text-sm font-bold text-[#0b6b3a] disabled:cursor-not-allowed disabled:text-[#87928a]">
          {sending && <LoaderCircle size={15} className="animate-spin" />}
          {sending ? t('auth.sendingOtp') : secondsLeft > 0 ? t('auth.resendIn', { seconds: secondsLeft }) : codePending ? t('auth.resend') : t('auth.requestCode')}
        </button>
        {error && <p role="alert" className="mt-4 rounded-xl bg-[#fff1ed] p-3 text-xs text-[#a94837]">{error}</p>}
      </div>
    </div>
  </main>;
}
