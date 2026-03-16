import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

import translationEN from './locales/en/translation.json';
import translationHU from './locales/hu/translation.json';

const resources = {
  en: { translation: translationEN },
  hu: { translation: translationHU }
};

i18n
  .use(LanguageDetector) // Böngésző nyelvének detektálása
  .use(initReactI18next) // react-i18next integráció
  .init({
    resources,
    fallbackLng: 'en', // Ha egy szöveg nincs meg magyarul, angolul jelenik meg
    interpolation: {
      escapeValue: false // React már amúgy is védekezik az XSS ellen
    }
  });

export default i18n;