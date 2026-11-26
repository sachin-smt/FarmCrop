import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

export const languages = [
  { code: 'en', nativeName: 'English', locale: 'en-IN', direction: 'ltr' },
  { code: 'hi', nativeName: 'हिन्दी', locale: 'hi-IN', direction: 'ltr' },
  { code: 'mr', nativeName: 'मराठी', locale: 'mr-IN', direction: 'ltr' },
  { code: 'bn', nativeName: 'বাংলা', locale: 'bn-IN', direction: 'ltr' },
  { code: 'te', nativeName: 'తెలుగు', locale: 'te-IN', direction: 'ltr' },
  { code: 'raj', nativeName: 'राजस्थानी', locale: 'hi-IN', direction: 'ltr' },
  { code: 'kn', nativeName: 'ಕನ್ನಡ', locale: 'kn-IN', direction: 'ltr' },
  { code: 'mai', nativeName: 'मैथिली', locale: 'hi-IN', direction: 'ltr' },
  { code: 'gu', nativeName: 'ગુજરાતી', locale: 'gu-IN', direction: 'ltr' },
  { code: 'ta', nativeName: 'தமிழ்', locale: 'ta-IN', direction: 'ltr' },
  { code: 'ml', nativeName: 'മലയാളം', locale: 'ml-IN', direction: 'ltr' },
  { code: 'pa', nativeName: 'ਪੰਜਾਬੀ', locale: 'pa-IN', direction: 'ltr' },
  { code: 'as', nativeName: 'অসমীয়া', locale: 'as-IN', direction: 'ltr' },
] as const;

const english = {
  nav: { home: 'Home', crops: 'Buy Crops', farmers: 'For Farmers', about: 'About', support: 'Support', login: 'Login / Sign Up', logout: 'Log out', language: 'Language' },
  hero: { eyebrow: 'Directly from the field', title: 'Fresh Harvest', titleAccent: 'Brighter Tomorrows', description: "Direct from our farmers' fields to your home. Better prices for farmers. Fresher food for everyone.", explore: 'Explore Crops', farmers: 'For Farmers', fair: 'Fair pricing', fresh: 'Fresh crops', payments: 'Safe payments', community: 'Trusted community' },
  marketplace: { demo: 'Demo marketplace', title: 'Fresh from Our Farms', subtitle: 'Handpicked crops from verified farmers', search: 'Search crops...', viewAll: 'View All', add: 'Add to Cart', empty: 'No demo crops match this filter yet.' },
  auth: { welcome: 'Welcome to FarmCrop', tagline: 'Fresh harvest, directly from farmers to you.', google: 'Continue with Google', facebook: 'Continue with Facebook', or: 'OR', mobile: 'Mobile number', sendOtp: 'Send OTP', verifyTitle: 'Verify your mobile number', verifyDescription: 'Enter the 6-digit code sent to your phone.', verify: 'Verify OTP', resend: 'Resend OTP', change: 'Change mobile number', missingConfig: 'Authentication is unavailable until the Spring Boot backend is connected.', phoneRequired: 'Please verify your mobile number before continuing.', terms: 'By continuing, you agree to our Terms and Privacy Policy.' },
  ai: { title: 'Krishi Saathi', subtitle: 'Your farming companion, always with you', greeting: 'Hello! I am your Krishi Saathi. Ask me about crops, market prices, weather, schemes, or your sales.', placeholder: 'Ask your question...', voice: 'Ask Krishi Saathi using voice', listening: 'Listening...', thinking: 'Krishi Saathi is thinking...', unavailable: 'Krishi Saathi is temporarily unavailable. Please try again.', send: 'Send question' },
  cart: { title: 'Your cart', empty: 'Your cart is waiting for something fresh.', subtotal: 'Demo subtotal', checkout: 'Continue to checkout', demo: 'Prices are for demonstration only. Payment is not connected.' },
  footer: { description: 'Connecting farmers directly with homes across India.', community: 'Made for farmers and communities.' },
};

const regional: Record<string, any> = {
  hi: { nav: { home: 'होम', crops: 'फसल खरीदें', farmers: 'किसानों के लिए', about: 'हमारे बारे में', support: 'सहायता', login: 'लॉगिन / साइन अप', logout: 'लॉग आउट', language: 'भाषा' }, hero: { eyebrow: 'खेत से सीधे', title: 'ताज़ी फसल', titleAccent: 'उज्ज्वल कल', description: 'हमारे किसानों के खेत से सीधे आपके घर तक। किसानों के लिए बेहतर दाम। सभी के लिए ताज़ा भोजन।', explore: 'फसलें देखें', farmers: 'किसानों के लिए', fair: 'उचित दाम', fresh: 'ताज़ी फसलें', payments: 'सुरक्षित भुगतान', community: 'विश्वसनीय समुदाय' }, marketplace: { demo: 'डेमो बाज़ार', title: 'हमारे खेतों से ताज़ा', subtitle: 'सत्यापित किसानों से चुनी हुई फसलें', search: 'फसल खोजें...', viewAll: 'सभी देखें', add: 'कार्ट में डालें', empty: 'इस फ़िल्टर से कोई डेमो फसल नहीं मिली।' }, auth: { welcome: 'FarmCrop में आपका स्वागत है', tagline: 'किसानों से सीधे आपके घर तक ताज़ी फसल।', google: 'Google से जारी रखें', facebook: 'Facebook से जारी रखें', or: 'या', mobile: 'मोबाइल नंबर', sendOtp: 'OTP भेजें', verifyTitle: 'मोबाइल नंबर सत्यापित करें', verifyDescription: 'अपने फोन पर भेजा गया 6 अंकों का कोड डालें।', verify: 'OTP सत्यापित करें', resend: 'OTP फिर भेजें', change: 'मोबाइल नंबर बदलें', missingConfig: 'ऑथेंटिकेशन तब उपलब्ध होगा जब Spring Boot बैकएंड कनेक्ट होगा।', phoneRequired: 'जारी रखने से पहले मोबाइल नंबर सत्यापित करें।', terms: 'जारी रखकर आप हमारी शर्तों और गोपनीयता नीति से सहमत हैं।' }, ai: { title: 'कृषि साथी', subtitle: 'आपका कृषि मित्र, हमेशा साथ', greeting: 'नमस्ते! मैं आपका कृषि साथी हूँ। फसल, बाजार भाव, मौसम, योजनाओं या बिक्री के बारे में पूछें।', placeholder: 'अपना सवाल लिखें...', voice: 'आवाज़ से कृषि साथी से पूछें', listening: 'सुन रहा हूँ...', thinking: 'कृषि साथी सोच रहा है...', unavailable: 'कृषि साथी अभी उपलब्ध नहीं है। कृपया फिर कोशिश करें।', send: 'सवाल भेजें' }, cart: { title: 'आपका कार्ट', empty: 'आपका कार्ट ताज़ी चीज़ों का इंतज़ार कर रहा है।', subtotal: 'डेमो योग', checkout: 'चेकआउट जारी रखें', demo: 'कीमतें केवल डेमो के लिए हैं। भुगतान जुड़ा नहीं है।' }, footer: { description: 'किसानों को भारत भर के घरों से सीधे जोड़ना।', community: 'किसानों और समुदायों के लिए बनाया गया।' } },
  mr: { nav: { home: 'मुख्यपृष्ठ', crops: 'पिके खरेदी करा', farmers: 'शेतकऱ्यांसाठी', about: 'आमच्याबद्दल', support: 'मदत', login: 'लॉगिन / साइन अप', logout: 'लॉग आउट', language: 'भाषा' }, hero: { title: 'ताजी कापणी', titleAccent: 'उज्वल उद्याची सुरुवात', explore: 'पिके पहा', farmers: 'शेतकऱ्यांसाठी' }, auth: { welcome: 'किसानबाजारमध्ये आपले स्वागत आहे', google: 'Google सह सुरू ठेवा', facebook: 'Facebook सह सुरू ठेवा', mobile: 'मोबाइल नंबर', sendOtp: 'OTP पाठवा', verifyTitle: 'मोबाइल नंबर सत्यापित करा', verify: 'OTP सत्यापित करा' } },
  bn: { nav: { home: 'হোম', crops: 'ফসল কিনুন', farmers: 'কৃষকদের জন্য', login: 'লগইন / সাইন আপ', language: 'ভাষা' }, hero: { title: 'তাজা ফসল', titleAccent: 'উজ্জ্বল আগামীকাল', explore: 'ফসল দেখুন' }, auth: { welcome: 'কিষানবাজারে স্বাগতম', mobile: 'মোবাইল নম্বর', sendOtp: 'OTP পাঠান', verifyTitle: 'মোবাইল নম্বর যাচাই করুন', verify: 'OTP যাচাই করুন' } },
  te: { nav: { home: 'హోమ్', crops: 'పంటలు కొనండి', farmers: 'రైతుల కోసం', login: 'లాగిన్ / సైన్ అప్', language: 'భాష' }, hero: { title: 'తాజా పంట', titleAccent: 'ప్రకాశవంతమైన రేపు', explore: 'పంటలను చూడండి' }, auth: { welcome: 'కిసాన్‌బజార్‌కు స్వాగతం', mobile: 'మొబైల్ నంబర్', sendOtp: 'OTP పంపండి', verifyTitle: 'మొబైల్ నంబర్‌ను ధృవీకరించండి', verify: 'OTP ధృవీకరించండి' } },
  gu: { nav: { home: 'હોમ', crops: 'પાક ખરીદો', farmers: 'ખેડૂતો માટે', login: 'લૉગિન / સાઇન અપ', language: 'ભાષા' }, hero: { title: 'તાજી લણણી', titleAccent: 'ઉજ્જવળ આવતીકાલ', explore: 'પાક જુઓ' }, auth: { welcome: 'કિસાનબજારમાં આપનું સ્વાગત છે', mobile: 'મોબાઇલ નંબર', sendOtp: 'OTP મોકલો', verifyTitle: 'મોબાઇલ નંબર ચકાસો', verify: 'OTP ચકાસો' } },
};

const resources = Object.fromEntries(languages.map(({ code }) => [code, { translation: { ...english, ...(regional[code] || {}) } }]));

void i18n.use(initReactI18next).init({ resources, lng: localStorage.getItem('kisanbazaar-language') || 'en', fallbackLng: 'en', interpolation: { escapeValue: false }, returnNull: false });

export default i18n;
