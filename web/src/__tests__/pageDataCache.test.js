import { clearCachedData, getCachedData, setCachedData } from '../shared/utils/pageDataCache';

describe('pageDataCache', () => {
  const originalNow = Date.now;

  afterEach(() => {
    Date.now = originalNow;
    clearCachedData('sample');
  });

  test('returns cached value before expiration', () => {
    Date.now = () => 1_000;
    setCachedData('sample', { ok: true }, 5_000);

    Date.now = () => 2_000;
    const cached = getCachedData('sample');

    expect(cached).toEqual({ ok: true });
  });

  test('returns null after expiration', () => {
    Date.now = () => 1_000;
    setCachedData('sample', 'value', 1_000);

    Date.now = () => 3_000;
    const cached = getCachedData('sample');

    expect(cached).toBeNull();
  });
});
