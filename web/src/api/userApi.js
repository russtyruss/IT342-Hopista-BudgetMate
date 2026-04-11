import axiosInstance from './axiosInstance';

export const getUsers = (params) =>
  axiosInstance.get('/users', { params });

export const deleteUser = (id) =>
  axiosInstance.delete(`/users/${id}`);
