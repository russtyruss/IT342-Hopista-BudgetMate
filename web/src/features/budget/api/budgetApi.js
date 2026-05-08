import axiosInstance from '../../../shared/api/axiosInstance';

export const getBudgets = (params) =>
  axiosInstance.get('/budgets', { params });

export const getBudget = (id) =>
  axiosInstance.get(`/budgets/${id}`);

export const createBudget = (data) =>
  axiosInstance.post('/budgets', data);

export const updateBudget = (id, data) =>
  axiosInstance.put(`/budgets/${id}`, data);

export const deleteBudget = (id) =>
  axiosInstance.delete(`/budgets/${id}`);
