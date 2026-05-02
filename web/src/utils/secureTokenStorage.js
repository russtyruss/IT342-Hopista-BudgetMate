import CryptoJS from 'crypto-js';

const ENCRYPTED_TOKEN_KEY = 'auth_token_enc_v1';
const LEGACY_PLAINTEXT_TOKEN_KEY = 'token';

const getSecret = () =>
  process.env.REACT_APP_TOKEN_SECRET || 'budgetmate-web-token-secret-v1';

const encrypt = (plainText) => CryptoJS.AES.encrypt(plainText, getSecret()).toString();

const decrypt = (cipherText) => {
  const bytes = CryptoJS.AES.decrypt(cipherText, getSecret());
  const text = bytes.toString(CryptoJS.enc.Utf8);
  return text || null;
};

const migrateLegacyTokenIfNeeded = () => {
  const legacyToken = localStorage.getItem(LEGACY_PLAINTEXT_TOKEN_KEY);
  if (!legacyToken) {
    return null;
  }

  const encrypted = encrypt(legacyToken);
  localStorage.setItem(ENCRYPTED_TOKEN_KEY, encrypted);
  localStorage.removeItem(LEGACY_PLAINTEXT_TOKEN_KEY);
  return legacyToken;
};

export const saveTokenSecurely = (token) => {
  if (!token) return;
  const encrypted = encrypt(token);
  localStorage.setItem(ENCRYPTED_TOKEN_KEY, encrypted);
  localStorage.removeItem(LEGACY_PLAINTEXT_TOKEN_KEY);
};

export const getSecureToken = () => {
  const migrated = migrateLegacyTokenIfNeeded();
  if (migrated) {
    return migrated;
  }

  const encrypted = localStorage.getItem(ENCRYPTED_TOKEN_KEY);
  if (!encrypted) {
    return null;
  }

  try {
    return decrypt(encrypted);
  } catch (_) {
    return null;
  }
};

export const removeSecureToken = () => {
  localStorage.removeItem(ENCRYPTED_TOKEN_KEY);
  localStorage.removeItem(LEGACY_PLAINTEXT_TOKEN_KEY);
};

export const hasSecureToken = () => Boolean(getSecureToken());
