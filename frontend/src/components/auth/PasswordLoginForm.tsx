import { useRef, useState, type FormEvent } from 'react';
import { Eye, EyeOff, LoaderCircle } from 'lucide-react';
import { useTranslation } from 'react-i18next';

interface PasswordLoginCredentials {
  usernameOrEmail: string;
  password: string;
}

interface PasswordLoginFormProps {
  submitLabel: string;
  onSubmit: (credentials: Omit<PasswordLoginCredentials, 'role'>) => Promise<void> | void;
}

export function PasswordLoginForm({ submitLabel, onSubmit }: PasswordLoginFormProps) {
  const { t } = useTranslation();
  const [usernameOrEmail, setUsernameOrEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [recoveryNotice, setRecoveryNotice] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const submitting = useRef(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current) return;

    if (!usernameOrEmail.trim()) {
      setError(t('auth.usernameRequired'));
      return;
    }
    if (!password) {
      setError(t('auth.passwordRequired'));
      return;
    }

    submitting.current = true;
    setBusy(true);
    setError('');
    try {
      await onSubmit({ usernameOrEmail: usernameOrEmail.trim(), password });
    } catch (loginError) {
      setError(loginError instanceof Error ? loginError.message : t('auth.invalidCredentials'));
    } finally {
      setPassword('');
      setBusy(false);
      submitting.current = false;
    }
  }

  return <form onSubmit={(event) => void submit(event)} noValidate className="space-y-4">
    <label className="block text-sm font-semibold text-[#31533d]">
      {t('auth.usernameOrEmail')}
      <input
        autoComplete="username"
        required
        value={usernameOrEmail}
        onChange={(event) => { setUsernameOrEmail(event.target.value); setError(''); }}
        className="mt-2 w-full rounded-xl border border-[#dfe7df] bg-white px-4 py-3 text-sm outline-none focus:border-[#0b6b3a] focus:ring-2 focus:ring-[#0b6b3a]/10"
      />
    </label>
    <label className="block text-sm font-semibold text-[#31533d]">
      {t('auth.password')}
      <span className="mt-2 flex rounded-xl border border-[#dfe7df] bg-white focus-within:border-[#0b6b3a] focus-within:ring-2 focus-within:ring-[#0b6b3a]/10">
        <input
          autoComplete="current-password"
          required
          type={showPassword ? 'text' : 'password'}
          value={password}
          onChange={(event) => { setPassword(event.target.value); setError(''); }}
          className="min-w-0 flex-1 rounded-l-xl bg-transparent px-4 py-3 text-sm outline-none"
        />
        <button
          type="button"
          onClick={() => setShowPassword((visible) => !visible)}
          aria-label={showPassword ? t('auth.hidePassword') : t('auth.showPassword')}
          aria-pressed={showPassword}
          className="grid w-12 shrink-0 place-items-center rounded-r-xl text-[#66736b] hover:bg-[#f5f8f4]"
        >
          {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
        </button>
      </span>
    </label>
    <div className="flex justify-end">
      <button
        type="button"
        onClick={() => setRecoveryNotice((visible) => !visible)}
        aria-expanded={recoveryNotice}
        className="text-xs font-bold text-[#0b6b3a] underline underline-offset-2"
      >
        {t('auth.forgotPassword')}
      </button>
    </div>
    {recoveryNotice && <p role="status" className="rounded-xl border border-[#f1e4be] bg-[#fffaf0] p-3 text-xs leading-5 text-[#6d5c31]">{t('auth.passwordRecoveryUnavailable')}</p>}
    {error && <p role="alert" className="rounded-xl bg-[#fff1ed] p-3 text-sm text-[#a94837]">{error}</p>}
    <button
      type="submit"
      disabled={busy}
      className="flex min-h-12 w-full items-center justify-center gap-2 rounded-xl bg-[#0b6b3a] px-5 py-3 text-sm font-bold text-white transition hover:bg-[#064e2a] disabled:cursor-not-allowed disabled:opacity-50"
    >
      {busy && <LoaderCircle size={17} className="animate-spin" />}
      {busy ? t('auth.signingIn') : submitLabel}
    </button>
  </form>;
}
