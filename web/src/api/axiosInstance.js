import axios from 'axios';
import { getSecureToken, hasSecureToken, removeSecureToken } from '../utils/secureTokenStorage';

const BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080/api/v1';

const axiosInstance = axios.create({
  baseURL: BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

const isAuthEndpoint = (url = '') =>
  url.includes('/auth/login')
  || url.includes('/auth/register')
  || url.includes('/auth/forgot-password')
  || url.includes('/auth/reset-password');

// Attach JWT token to every request
axiosInstance.interceptors.request.use(
  (config) => {
    const token = getSecureToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Handle 401 - clear token and redirect to login
axiosInstance.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const requestUrl = error.config?.url || '';
    const hasToken = hasSecureToken();
    const isLoginPage = window.location.pathname === '/login';

    if ((status === 401 || status === 403) && !isAuthEndpoint(requestUrl) && hasToken) {
      removeSecureToken();
      localStorage.removeItem('user');
      if (!isLoginPage) {
        window.location.replace('/login');
      }
    }
    return Promise.reject(error);
  }
);

export default axiosInstance;
