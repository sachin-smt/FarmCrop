import { useState, type FormEvent } from 'react';
import { BadgeCheck, LoaderCircle, Phone } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../../hooks/useAuth';

/** 10-digit national part of a stored Indian number in any form (+91…, 91…, plain), else ''. */
function nationalDigits(phone: string | null) {
  const match = phone?.replace(/[\s-]/g, '').match(/^(?:\+?91)?([6-9]\d{9})$/);
  return match ? match[1] : '';
}

/** Profile row for the signed-in user's mobile number: shows whether it is verified and lets them verify it by OTP. */
export function PhoneVerificationSection() {
  const { t } = useTranslation();
  const { profile, methods, sendOtp } = useAuth();
  const navigate = useNavigate();
  const [phone, setPhone] = useState(() => nationalDigits(profile?.phoneNumber ?? null));
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');

  if (!profile) return null;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!/^[6-9]\d{9}$/.test(phone)) {
      setNotice(t('auth.invalidPhone'));
      return;
    }
    setBusy(true);
    setNotice('');
    const e164 = `+91${phone}`;
    try {
      await sendOtp(e164);
      navigate(`/verify-phone?purpose=account&phone=${encodeURIComponent(e164)}`);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : t('auth.signInFailed'));
    } finally {
      setBusy(false);
    }
  }

  return <div>
    <dt className="text-[#87928a]">Phone</dt>
    <dd className="mt-0.5 flex flex-wrap items-center gap-2 font-semibold">
      <span className="break-all">{profile.phoneNumber || '-'}</span>
      {profile.phoneNumber && (profile.phoneVerified
        ? <span className="inline-flex items-center gap-1 rounded-full bg-[#e8f5ec] px-2 py-0.5 text-[11px] font-bold text-[#0b6b3a]"><BadgeCheck size={13} /> {t('auth.phoneVerifiedBadge')}</span>
        : <span className="rounded-full bg-[#fff7e3] px-2 py-0.5 text-[11px] font-bold text-[#8a6a14]">{t('auth.phoneNotVerifiedBadge')}</span>)}
    </dd>
    {profile.phoneVerified
      ? <p className="mt-1 text-xs text-[#66736b]">{t('auth.otpLoginEnabled')}</p>
      : <form onSubmit={(event) => void submit(event)} className="mt-3 rounded-2xl border border-[#e5eae3] bg-[#fbfcf9] p-3 sm:p-4">
        <p className="text-xs leading-5 text-[#66736b]">{t('auth.verifyPhoneHint')}</p>
        <div className="mt-3 flex flex-col gap-2 sm:flex-row">
          <label className="flex min-w-0 flex-1 items-center gap-2 rounded-xl border border-[#dfe7df] bg-white px-3 py-2.5 focus-within:border-[#0b6b3a] focus-within:ring-2 focus-within:ring-[#0b6b3a]/10">
            <Phone size={16} className="shrink-0 text-[#0b6b3a]" />
            <span className="shrink-0 border-r border-[#dfe7df] pr-2 text-sm font-semibold text-[#31533d]">+91</span>
            <span className="sr-only">{t('auth.mobile')}</span>
            <input value={phone} onChange={(event) => { setPhone(event.target.value.replace(/\D/g, '').slice(0, 10)); setNotice(''); }}
              type="tel" inputMode="tel" autoComplete="tel-national" maxLength={10} placeholder="10-digit mobile number"
              disabled={!methods.phone} className="min-w-0 flex-1 bg-transparent text-sm outline-none" />
          </label>
          <button type="submit" disabled={busy || phone.length !== 10 || !methods.phone}
            className="flex items-center justify-center gap-2 rounded-xl bg-[#0b6b3a] px-4 py-2.5 text-sm font-bold text-white disabled:cursor-not-allowed disabled:opacity-50">
            {busy && <LoaderCircle size={16} className="animate-spin" />}{busy ? t('auth.sendingOtp') : t('auth.verifyPhoneAction')}
          </button>
        </div>
        {!methods.phone && <p className="mt-2 text-xs text-[#87928a]">{t('auth.phoneOtpOff')}</p>}
        {notice && <p role="status" className="mt-2 text-xs text-[#a94837]">{notice}</p>}
      </form>}
  </div>;
}
