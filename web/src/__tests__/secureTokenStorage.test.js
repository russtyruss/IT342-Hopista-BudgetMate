import {
  getSecureToken,
  removeSecureToken,
  saveTokenSecurely,
} from '../shared/utils/secureTokenStorage';

describe('secureTokenStorage', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  test('saves and retrieves encrypted token', () => {
    saveTokenSecurely('token-123');
    expect(getSecureToken()).toBe('token-123');
  });

  test('removes tokens', () => {
    saveTokenSecurely('token-456');
    removeSecureToken();
    expect(getSecureToken()).toBeNull();
  });

  test('migrates legacy plaintext token', () => {
    localStorage.setItem('token', 'legacy-token');
    expect(getSecureToken()).toBe('legacy-token');
  });
});
