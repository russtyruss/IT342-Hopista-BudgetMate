import axiosInstance from './axiosInstance';

export const getExchangeRates = (base = 'PHP') =>
  axiosInstance.get('/exchange-rates', { params: { base } });
