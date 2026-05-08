const DEFAULT_TTL_MS = 30_000;

const store = new Map();

export const setCachedData = (key, value, ttlMs = DEFAULT_TTL_MS) => {
  store.set(key, {
    value,
    expiresAt: Date.now() + ttlMs,
  });
};

export const getCachedData = (key) => {
  const entry = store.get(key);
  if (!entry) {
    return null;
  }

  if (Date.now() > entry.expiresAt) {
    store.delete(key);
    return null;
  }

  return entry.value;
};

export const clearCachedData = (key) => {
  store.delete(key);
};
