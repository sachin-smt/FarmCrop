import { useTranslation } from 'react-i18next';
import { languages } from '../i18n';
import { useAuth } from './useAuth';

export function useLanguage() {
  const { i18n: instance } = useTranslation();
  const { profile, updatePreferredLanguage } = useAuth();
  const currentLanguage = languages.find((item) => item.code === instance.language) || languages[0];
  const setLanguage = async (code: string) => {
    await instance.changeLanguage(code);
    localStorage.setItem('kisanbazaar-language', code);
    if (profile?.id) await updatePreferredLanguage(code);
  };
  return { language: currentLanguage.code, currentLanguage, availableLanguages: languages, setLanguage };
}
