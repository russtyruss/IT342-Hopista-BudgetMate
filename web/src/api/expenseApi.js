import axiosInstance from './axiosInstance';

export const getExpenses = (params) =>
  axiosInstance.get('/expenses', { params });

export const getExpense = (id) =>
  axiosInstance.get(`/expenses/${id}`);

export const createExpense = (data) =>
  axiosInstance.post('/expenses', data);

export const updateExpense = (id, data) =>
  axiosInstance.put(`/expenses/${id}`, data);

export const deleteExpense = (id) =>
  axiosInstance.delete(`/expenses/${id}`);
