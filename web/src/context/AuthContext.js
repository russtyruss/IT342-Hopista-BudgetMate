import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import { login as apiLogin, logout as apiLogout, getMe } from '../api/authApi';

const AuthContext = createContext(null);
const MAX_TIMEOUT_MS = 2147483647;

export const AuthProvider = ({ children }) => {
  const logoutTimerRef = useRef(null);
  const [user, setUser] = useState(() => {
    const stored = localStorage.getItem('user');
    return stored ? JSON.parse(stored) : null;
  });
  const [loading, setLoading] = useState(true);

  const clearLogoutTimer = () => {
    if (logoutTimerRef.current) {
      clearTimeout(logoutTimerRef.current);
      logoutTimerRef.current = null;
    }
  };

  const clearAuthStorage = () => {
    localStorage.removeItem('token');
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

    const token = localStorage.getItem('token');
    const expiryMs = decodeTokenExpiryMs(token);
    if (!expiryMs) return;

    const delay = expiryMs - Date.now();
    if (delay <= 0) {
      clearAuthStorage();
      setUser(null);
      return;
    }

    const timeout = Math.min(delay, MAX_TIMEOUT_MS);
    logoutTimerRef.current = setTimeout(() => {
      if (delay > MAX_TIMEOUT_MS) {
        scheduleAutoLogout();
        return;
      }

      clearAuthStorage();
      setUser(null);
      window.location.href = '/login';
    }, timeout);
  }, []);

  // Verify token on mount
  useEffect(() => {
    const token = localStorage.getItem('token');
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
          setUser(res.data);
          scheduleAutoLogout();
        })
        .catch(() => {
          clearAuthStorage();
          setUser(null);
        })
        .finally(() => setLoading(false));
    } else {
      setLoading(false);
    }
    return () => clearLogoutTimer();
  }, [scheduleAutoLogout]);

  const login = useCallback(async (email, password) => {
    const res = await apiLogin({ email, password });
    const { accessToken, ...userInfo } = res.data;
    localStorage.setItem('token', accessToken);
    localStorage.setItem('user', JSON.stringify(userInfo));
    setUser(userInfo);
    scheduleAutoLogout();
    return userInfo;
  }, [scheduleAutoLogout]);

  const logout = useCallback(async () => {
    try { await apiLogout(); } catch (_) {}
    clearLogoutTimer();
    clearAuthStorage();
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider value={{ user, login, logout, loading }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
