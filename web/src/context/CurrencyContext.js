import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { getExchangeRates } from '../api/exchangeRateApi';

const DEFAULT_CURRENCY = 'PHP';
const SUPPORTED_CURRENCIES = ['PHP', 'USD', 'EUR', 'JPY', 'GBP', 'AUD', 'CAD', 'SGD', 'HKD'];

const CurrencyContext = createContext(null);

export const CurrencyProvider = ({ children }) => {
  const [selectedCurrency, setSelectedCurrency] = useState(() => localStorage.getItem('displayCurrency') || DEFAULT_CURRENCY);
  const [rates, setRates] = useState({ [DEFAULT_CURRENCY]: 1 });

  useEffect(() => {
    localStorage.setItem('displayCurrency', selectedCurrency);
  }, [selectedCurrency]);

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (!token) {
      setRates({ [DEFAULT_CURRENCY]: 1 });
      return;
    }

    let mounted = true;
    getExchangeRates(DEFAULT_CURRENCY)
      .then((res) => {
        const fetchedRates = res.data?.conversion_rates;
        if (mounted && fetchedRates) {
          setRates({ ...fetchedRates, [DEFAULT_CURRENCY]: 1 });
        }
      })
      .catch(() => {
        if (mounted) {
          setRates({ [DEFAULT_CURRENCY]: 1 });
        }
      });

    return () => {
      mounted = false;
    };
  }, []);

  const convertAmount = useCallback(
    (amount, fromCurrency = DEFAULT_CURRENCY) => {
      const numericAmount = Number(amount || 0);
      const from = (fromCurrency || DEFAULT_CURRENCY).toUpperCase();
      const to = (selectedCurrency || DEFAULT_CURRENCY).toUpperCase();

      if (!Number.isFinite(numericAmount)) return 0;
      if (from === to) return numericAmount;

      const fromRate = rates[from];
      const toRate = rates[to];
      if (!fromRate || !toRate) return numericAmount;

      return numericAmount * (toRate / fromRate);
    },
    [rates, selectedCurrency]
  );

  const formatCurrency = useCallback(
    (amount, fromCurrency = DEFAULT_CURRENCY) => {
      const converted = convertAmount(amount, fromCurrency);
      return new Intl.NumberFormat('en-PH', {
        style: 'currency',
        currency: selectedCurrency,
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(converted);
    },
    [convertAmount, selectedCurrency]
  );

  const value = useMemo(
    () => ({
      selectedCurrency,
      setSelectedCurrency,
      supportedCurrencies: SUPPORTED_CURRENCIES,
      formatCurrency,
      convertAmount,
    }),
    [selectedCurrency, formatCurrency, convertAmount]
  );

  return <CurrencyContext.Provider value={value}>{children}</CurrencyContext.Provider>;
};

export const useCurrency = () => useContext(CurrencyContext);
