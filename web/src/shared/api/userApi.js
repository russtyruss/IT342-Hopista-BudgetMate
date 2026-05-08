import axiosInstance from './axiosInstance';

export const getUsers = (params) =>
  axiosInstance.get('/users', { params });

export const deleteUser = (id) =>
  axiosInstance.delete(`/users/${id}`);

export const updateMyName = (data) =>
  axiosInstance.put('/users/me/name', data);

export const changeMyPassword = (data) =>
  axiosInstance.put('/users/me/password', data);

export const uploadMyProfileImage = (file) => {
  const formData = new FormData();
  formData.append('file', file);

  return axiosInstance.post('/users/me/profile-image', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
};

export const downloadMyProfileImage = (cacheKey) =>
  axiosInstance.get('/users/me/profile-image', {
    responseType: 'blob',
    params: cacheKey ? { t: cacheKey } : undefined,
    headers: {
      'Cache-Control': 'no-cache',
      Pragma: 'no-cache',
    },
  });
