import axiosInstance from './axiosInstance';

export const register = (data) =>
  axiosInstance.post('/auth/register', data);

export const login = (data) =>
  axiosInstance.post('/auth/login', data);

export const logout = () =>
  axiosInstance.post('/auth/logout');

export const getMe = () =>
  axiosInstance.get('/auth/me');

export const forgotPassword = (data) =>
  axiosInstance.post('/auth/forgot-password', data);

export const resetPassword = (data) =>
  axiosInstance.post('/auth/reset-password', data);
