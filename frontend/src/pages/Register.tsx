import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowLeft, Eye, EyeOff, Leaf, LoaderCircle, MailCheck, ShieldCheck } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { LanguageSelector } from '../components/i18n/LanguageSelector';
import { defaultLandingPath, useAuth } from '../hooks/useAuth';
import { ApiError } from '../services/apiClient';
import type { PendingRegistration, RegisterInput } from '../services/authService';

type FieldName = keyof RegisterInput;

const emptyForm: RegisterInput = { fullName: '', username: '', email: '', phone: '', password: '', confirmPassword: '' };

const inputClass = 'mt-2 w-full rounded-xl border bg-white px-4 py-3 text-sm outline-none focus:border-[#0b6b3a] focus:ring-2 focus:ring-[#0b6b3a]/10';

function Field({ id, label, hint, error, children }: { id: string; label: string; hint?: string; error?: string; children: ReactNode }) {
  return <div>
    <label htmlFor={id} className="block text-sm font-semibold text-[#31533d]">{label}</label>
    {children}
    {error ? <span id={`${id}-message`} role="alert" className="mt-1 block text-xs font-medium text-[#a94837]">{error}</span>
      : hint ? <span id={`${id}-message`} className="mt-1 block text-xs font-normal text-[#87928a]">{hint}</span> : null}
  </div>;
}

/** Seconds until `iso`, never negative. */
function secondsUntil(iso: string, now: number) {
  return Math.max(0, Math.ceil((Date.parse(iso) - now) / 1000));
}

/**
 * Step 2 of registration: the 6-digit code sent by email. The password typed in step 1 is sent along (kept only
 * in memory) so that only the person who chose it can activate the account.
 */
function EmailCodeStep({ pending, password, initialUsername, onPendingChange, onBack }: {
  pending: PendingRegistration;
  password: string;
  initialUsername: string;
  onPendingChange: (next: PendingRegistration) => void;
  onBack: () => void;
}) {
  const { t, i18n } = useTranslation();
  const { verifyRegistration, resendRegistrationCode } = useAuth();
  const navigate = useNavigate();
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [done, setDone] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  // Shown only if the username was taken before the account could be created; the same code is reused.
  const [usernameTaken, setUsernameTaken] = useState(false);
  const [username, setUsername] = useState(initialUsername);
  const [usernameError, setUsernameError] = useState('');
  const resendIn = secondsUntil(pending.resendAvailableAt, now);
  const expiresAt = new Date(pending.expiresAt).toLocaleTimeString(i18n.language, { hour: '2-digit', minute: '2-digit' });

  useEffect(() => {
    if (resendIn <= 0) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [resendIn]);

  async function verify(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || code.length !== 6) return;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const profile = await verifyRegistration({ email: pending.email, otp: code, password, ...(usernameTaken ? { username: username.trim() } : {}) });
      setDone(true);
      navigate(defaultLandingPath(profile), { replace: true });
    } catch (caught) {
      const fieldErrors = caught instanceof ApiError ? caught.fieldErrorMap() : {};
      if (fieldErrors.username) {
        // The code was correct and is still valid: let the user pick another username and submit again.
        setUsernameTaken(true);
        setUsernameError(fieldErrors.username);
        setError(t('auth.usernameTakenAtVerify'));
      } else {
        if (!(caught instanceof ApiError && caught.status === 409)) setCode('');
        setError(caught instanceof Error ? caught.message : t('auth.signInFailed'));
      }
    } finally {
      setBusy(false);
    }
  }

  async function resend() {
    setSending(true);
    setError('');
    setNotice('');
    try {
      onPendingChange(await resendRegistrationCode(pending.email));
      setNow(Date.now());
      setCode('');
      setNotice(t('auth.verifyEmailSent') + ' ' + pending.email);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t('auth.signInFailed'));
    } finally {
      setSending(false);
    }
  }

  return <div className="mt-6">
    <button type="button" onClick={onBack} className="mb-6 inline-flex items-center gap-2 text-xs font-bold text-[#0b6b3a]"><ArrowLeft size={15} /> {t('auth.changeDetails')}</button>
    <div className="flex items-start gap-3 rounded-2xl bg-[#e8f5ec] p-4 text-sm text-[#31533d]">
      <MailCheck size={20} className="mt-0.5 shrink-0 text-[#0b6b3a]" />
      <p className="min-w-0">{t('auth.verifyEmailSent')} <b className="break-all text-[#122018]">{pending.email}</b>. {t('auth.codeExpiresAt', { time: expiresAt })}</p>
    </div>
    <form onSubmit={(event) => void verify(event)} noValidate className="mt-5">
      <label htmlFor="register-email-code" className="block text-sm font-semibold text-[#31533d]">{t('auth.emailCode')}</label>
      <input id="register-email-code" value={code} onChange={(event) => { setCode(event.target.value.replace(/\D/g, '').slice(0, 6)); setError(''); }}
        inputMode="numeric" autoComplete="one-time-code" autoFocus disabled={busy || done} maxLength={6}
        aria-describedby="register-email-code-message" aria-invalid={Boolean(error)}
        className="mt-2 w-full rounded-xl border border-[#dfe7df] bg-white px-4 py-4 text-center text-2xl tracking-[.5em] outline-none focus:border-[#0b6b3a] focus:ring-2 focus:ring-[#0b6b3a]/10 disabled:bg-[#f5f8f4]"
        placeholder="------" />
      <span id="register-email-code-message" className="mt-1 block text-xs text-[#87928a]">{t('auth.checkSpam')}</span>
      {usernameTaken && <div className="mt-4">
        <label htmlFor="register-new-username" className="block text-sm font-semibold text-[#31533d]">{t('auth.username')}</label>
        <input id="register-new-username" value={username} autoComplete="username" autoCapitalize="none" spellCheck={false}
          aria-describedby="register-new-username-message" aria-invalid={Boolean(usernameError)}
          onChange={(event) => { setUsername(event.target.value.toLowerCase()); setUsernameError(''); }}
          className={`mt-2 w-full rounded-xl border bg-white px-4 py-3 text-sm outline-none focus:border-[#0b6b3a] focus:ring-2 focus:ring-[#0b6b3a]/10 ${usernameError ? 'border-[#d9a294]' : 'border-[#dfe7df]'}`} />
        <span id="register-new-username-message" role={usernameError ? 'alert' : undefined} className={`mt-1 block text-xs ${usernameError ? 'font-medium text-[#a94837]' : 'text-[#87928a]'}`}>{usernameError || t('auth.usernameHint')}</span>
      </div>}
      {error && <p role="alert" className="mt-4 rounded-xl bg-[#fff1ed] p-3 text-sm text-[#a94837]">{error}</p>}
      {notice && !error && <p role="status" className="mt-4 rounded-xl bg-[#e8f5ec] p-3 text-sm text-[#0b6b3a]">{notice}</p>}
      {done && <p role="status" className="mt-4 rounded-xl bg-[#e8f5ec] p-3 text-sm font-semibold text-[#0b6b3a]">{t('auth.accountCreated')}</p>}
      <button type="submit" disabled={busy || done || code.length !== 6} className="mt-5 flex min-h-12 w-full items-center justify-center gap-2 rounded-xl bg-[#0b6b3a] px-5 py-3 text-sm font-bold text-white transition hover:bg-[#064e2a] disabled:cursor-not-allowed disabled:opacity-50">
        {busy && <LoaderCircle size={17} className="animate-spin" />}
        {busy ? t('auth.verifying') : t('auth.verifyAndCreate')}
      </button>
    </form>
    <button type="button" onClick={() => void resend()} disabled={sending || done || resendIn > 0} className="mt-4 flex w-full items-center justify-center gap-2 text-sm font-bold text-[#0b6b3a] disabled:cursor-not-allowed disabled:text-[#87928a]">
      {sending && <LoaderCircle size={15} className="animate-spin" />}
      {sending ? t('auth.sendingOtp') : resendIn > 0 ? t('auth.resendIn', { seconds: resendIn }) : t('auth.resend')}
    </button>
  </div>;
}

export function Register() {
  const { t } = useTranslation();
  const { register, signInWithOAuth, methods } = useAuth();
  const navigate = useNavigate();
  const [googleBusy, setGoogleBusy] = useState(false);
  const [googleError, setGoogleError] = useState('');

  async function continueWithGoogle() {
    setGoogleBusy(true);
    setGoogleError('');
    try {
      const signedIn = await signInWithOAuth('google');
      if (signedIn) navigate(defaultLandingPath(signedIn), { replace: true });
    } catch (error) {
      setGoogleError(error instanceof Error ? error.message : t('auth.signInFailed'));
    } finally {
      setGoogleBusy(false);
    }
  }
  const [form, setForm] = useState<RegisterInput>(emptyForm);
  const [errors, setErrors] = useState<Partial<Record<FieldName, string>>>({});
  const [formError, setFormError] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [busy, setBusy] = useState(false);
  const [pending, setPending] = useState<PendingRegistration | null>(null);
  const submitting = useRef(false);

  function update(field: FieldName, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
    setFormError('');
  }

  function validate(): boolean {
    const next: Partial<Record<FieldName, string>> = {};
    (Object.keys(form) as FieldName[]).forEach((field) => {
      if (!form[field].trim()) next[field] = t('auth.fieldRequired');
    });
    if (form.password && form.confirmPassword && form.password !== form.confirmPassword) {
      next.confirmPassword = t('auth.passwordsDoNotMatch');
    }
    setErrors(next);
    return Object.keys(next).length === 0;
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current || !validate()) return;
    submitting.current = true;
    setBusy(true);
    try {
      setPending(await register({ ...form, fullName: form.fullName.trim(), username: form.username.trim(), email: form.email.trim() }));
      // On small screens the form was scrolled down; show the code step from its heading.
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (caught) {
      if (caught instanceof ApiError && caught.fieldErrors.length > 0) {
        setErrors(caught.fieldErrorMap() as Partial<Record<FieldName, string>>);
      }
      setFormError(caught instanceof Error ? caught.message : t('auth.missingConfig'));
    } finally {
      setBusy(false);
      submitting.current = false;
    }
  }

  const border = (field: FieldName) => (errors[field] ? 'border-[#d9a294]' : 'border-[#dfe7df]');

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
          <h2 className="serif text-3xl font-bold text-[#122018]">{pending ? t('auth.verifyEmailTitle') : t('auth.registerTitle')}</h2>
          {pending ? <EmailCodeStep pending={pending} password={form.password} initialUsername={form.username} onPendingChange={setPending} onBack={() => setPending(null)} /> : <>
          <p className="mt-2 text-sm text-[#66736b]">{t('auth.registerTagline')}</p>
          {methods.google && <>
            <button type="button" onClick={() => void continueWithGoogle()} disabled={googleBusy} className="mt-6 flex w-full items-center justify-center gap-2 rounded-xl border border-[#dfe7df] bg-white py-3 text-sm font-bold text-[#31533d] transition hover:border-[#0b6b3a] hover:bg-[#f5f8f4] disabled:cursor-wait disabled:opacity-60">
              {googleBusy ? <LoaderCircle size={17} className="animate-spin text-[#0b6b3a]" /> : <span className="bg-gradient-to-r from-[#4285f4] via-[#34a853] to-[#ea4335] bg-clip-text text-lg text-transparent">G</span>}
              {t('auth.google')}
            </button>
            {googleError && <p role="alert" className="mt-3 rounded-xl border border-[#f1e4be] bg-[#fffaf0] p-3 text-xs leading-5 text-[#6d5c31]">{googleError}</p>}
            <div className="mt-6 flex items-center gap-3 text-xs text-[#9aa69e]"><span className="h-px flex-1 bg-[#e5eae3]" />{t('auth.or')}<span className="h-px flex-1 bg-[#e5eae3]" /></div>
          </>}
          <form onSubmit={(event) => void submit(event)} noValidate className="mt-6 space-y-4">
            <Field id="register-fullName" label={t('auth.fullName')} error={errors.fullName}>
              <input id="register-fullName" aria-describedby="register-fullName-message" aria-invalid={Boolean(errors.fullName)} autoComplete="name" value={form.fullName} onChange={(event) => update('fullName', event.target.value)} className={`${inputClass} ${border('fullName')}`} />
            </Field>
            <Field id="register-username" label={t('auth.username')} hint={t('auth.usernameHint')} error={errors.username}>
              <input id="register-username" aria-describedby="register-username-message" aria-invalid={Boolean(errors.username)} autoComplete="username" autoCapitalize="none" spellCheck={false} value={form.username} onChange={(event) => update('username', event.target.value.toLowerCase())} className={`${inputClass} ${border('username')}`} />
            </Field>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field id="register-email" label={t('auth.email')} error={errors.email}>
                <input id="register-email" aria-describedby="register-email-message" aria-invalid={Boolean(errors.email)} type="email" autoComplete="email" inputMode="email" value={form.email} onChange={(event) => update('email', event.target.value)} className={`${inputClass} ${border('email')}`} />
              </Field>
              <Field id="register-phone" label={t('auth.phone')} error={errors.phone}>
                <input id="register-phone" aria-describedby="register-phone-message" aria-invalid={Boolean(errors.phone)} type="tel" autoComplete="tel" inputMode="tel" placeholder="+91 98765 43210" value={form.phone} onChange={(event) => update('phone', event.target.value.replace(/[^\d+\s-]/g, '').slice(0, 20))} className={`${inputClass} ${border('phone')}`} />
              </Field>
            </div>
            <Field id="register-password" label={t('auth.password')} hint={t('auth.passwordHint')} error={errors.password}>
              <span className={`mt-2 flex rounded-xl border bg-white focus-within:border-[#0b6b3a] focus-within:ring-2 focus-within:ring-[#0b6b3a]/10 ${border('password')}`}>
                <input id="register-password" aria-describedby="register-password-message" aria-invalid={Boolean(errors.password)} type={showPassword ? 'text' : 'password'} autoComplete="new-password" value={form.password} onChange={(event) => update('password', event.target.value)} className="min-w-0 flex-1 rounded-l-xl bg-transparent px-4 py-3 text-sm outline-none" />
                <button type="button" onClick={() => setShowPassword((visible) => !visible)} aria-label={showPassword ? t('auth.hidePassword') : t('auth.showPassword')} aria-pressed={showPassword} className="grid w-12 shrink-0 place-items-center rounded-r-xl text-[#66736b] hover:bg-[#f5f8f4]">
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </span>
            </Field>
            <Field id="register-confirmPassword" label={t('auth.confirmPassword')} error={errors.confirmPassword}>
              <input id="register-confirmPassword" aria-describedby="register-confirmPassword-message" aria-invalid={Boolean(errors.confirmPassword)} type={showPassword ? 'text' : 'password'} autoComplete="new-password" value={form.confirmPassword} onChange={(event) => update('confirmPassword', event.target.value)} className={`${inputClass} ${border('confirmPassword')}`} />
            </Field>
            {formError && <p role="alert" className="rounded-xl bg-[#fff1ed] p-3 text-sm text-[#a94837]">{formError}</p>}
            <button type="submit" disabled={busy} className="flex min-h-12 w-full items-center justify-center gap-2 rounded-xl bg-[#0b6b3a] px-5 py-3 text-sm font-bold text-white transition hover:bg-[#064e2a] disabled:cursor-not-allowed disabled:opacity-50">
              {busy && <LoaderCircle size={17} className="animate-spin" />}
              {busy ? t('auth.registering') : t('auth.register')}
            </button>
          </form>
          </>}
          <p className="mt-6 text-center text-sm text-[#66736b]">{t('auth.haveAccount')} <Link to="/login" className="font-bold text-[#0b6b3a] underline underline-offset-2">{t('auth.login')}</Link></p>
          <p className="mt-3 text-center text-xs text-[#87928a]">{t('auth.terms')}</p>
        </div>
      </section>
    </div>
  </main>;
}
