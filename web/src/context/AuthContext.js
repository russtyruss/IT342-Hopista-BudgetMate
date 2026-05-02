import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import { login as apiLogin, logout as apiLogout, getMe } from '../api/authApi';
import { getSecureToken, removeSecureToken, saveTokenSecurely } from '../utils/secureTokenStorage';

const AuthContext = createContext(null);
const MAX_TIMEOUT_MS = 2147483647;

export const AuthProvider = ({ children }) => {
  const logoutTimerRef = useRef(null);
  const [user, setUser] = useState(() => {
    const stored = localStorage.getItem('user');
    return stored ? JSON.parse(stored) : null;
  });
  const [loading, setLoading] = useState(true);

  const setAndPersistUser = useCallback((nextUser) => {
    if (nextUser) {
      localStorage.setItem('user', JSON.stringify(nextUser));
    } else {
      localStorage.removeItem('user');
    }
    setUser(nextUser);
  }, []);

  const clearLogoutTimer = () => {
    if (logoutTimerRef.current) {
      clearTimeout(logoutTimerRef.current);
      logoutTimerRef.current = null;
    }
  };

  const clearAuthStorage = () => {
    removeSecureToken();
    localStorage.removeItem('user');
  };

  const decodeTokenExpiryMs = (token) => {
    if (!token) return null;
    try {
      const payload = token.split('.')[1];
      const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
      const decoded = JSON.parse(window.atob(normalized));
      return decoded?.exp ? decoded.exp * 1000 : null;
    } catch (_) {
      return null;
    }
  };

  const scheduleAutoLogout = useCallback(() => {
    clearLogoutTimer();

    const token = getSecureToken();
    const expiryMs = decodeTokenExpiryMs(token);
    if (!expiryMs) return;

    const delay = expiryMs - Date.now();
    if (delay <= 0) {
      clearAuthStorage();
        setAndPersistUser(null);
      return;
    }

    const timeout = Math.min(delay, MAX_TIMEOUT_MS);
    logoutTimerRef.current = setTimeout(() => {
      if (delay > MAX_TIMEOUT_MS) {
        scheduleAutoLogout();
        return;
      }

      clearAuthStorage();
      setAndPersistUser(null);
      window.location.href = '/login';
    }, timeout);
  }, [setAndPersistUser]);

  const refreshUser = useCallback(async () => {
    const res = await getMe();
    setAndPersistUser(res.data);
    return res.data;
  }, [setAndPersistUser]);

  const patchUser = useCallback((patch) => {
    setUser((prevUser) => {
      const nextUser = { ...(prevUser || {}), ...(patch || {}) };
      localStorage.setItem('user', JSON.stringify(nextUser));
      return nextUser;
    });
  }, []);

  // Verify token on mount
  useEffect(() => {
    const token = getSecureToken();
    if (token) {
      const expiryMs = decodeTokenExpiryMs(token);
      if (expiryMs && expiryMs <= Date.now()) {
        clearAuthStorage();
        setUser(null);
        setLoading(false);
        return;
      }

      getMe()
        .then((res) => {
          setAndPersistUser(res.data);
          scheduleAutoLogout();
        })
        .catch(() => {
          clearAuthStorage();
          setAndPersistUser(null);
        })
        .finally(() => setLoading(false));
    } else {
      setLoading(false);
    }
    return () => clearLogoutTimer();
  }, [scheduleAutoLogout, setAndPersistUser]);

  const login = useCallback(async (email, password) => {
    const res = await apiLogin({ email, password });
    const { accessToken } = res.data;
    saveTokenSecurely(accessToken);
    const userInfo = await refreshUser();
    scheduleAutoLogout();
    return userInfo;
  }, [refreshUser, scheduleAutoLogout]);

  const loginWithOAuthToken = useCallback(async (token) => {
    if (!token) {
      throw new Error('OAuth token is missing.');
    }

    saveTokenSecurely(token);
    const userInfo = await refreshUser();
    scheduleAutoLogout();
    return userInfo;
  }, [refreshUser, scheduleAutoLogout]);

  const logout = useCallback(async () => {
    try { await apiLogout(); } catch (_) {}
    clearLogoutTimer();
    clearAuthStorage();
    setAndPersistUser(null);
  }, [setAndPersistUser]);

  return (
    <AuthContext.Provider value={{ user, login, loginWithOAuthToken, logout, loading, refreshUser, patchUser }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
