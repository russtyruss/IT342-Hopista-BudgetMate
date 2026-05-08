import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

const DEFAULT_CURRENCY = 'PHP';
const SUPPORTED_CURRENCIES = ['PHP', 'USD', 'EUR', 'JPY', 'GBP', 'AUD', 'CAD', 'SGD', 'HKD'];
const CURRENCY_SYMBOLS = {
  PHP: '₱',
  USD: '$',
  EUR: '€',
  JPY: '¥',
  GBP: '£',
  AUD: '$',
  CAD: '$',
  SGD: '$',
  HKD: '$',
};

const CurrencyContext = createContext(null);

export const CurrencyProvider = ({ children }) => {
  const [selectedCurrency, setSelectedCurrency] = useState(() => localStorage.getItem('displayCurrency') || DEFAULT_CURRENCY);

  useEffect(() => {
    localStorage.setItem('displayCurrency', selectedCurrency);
  }, [selectedCurrency]);

  const convertAmount = useCallback(
    (amount, fromCurrency = DEFAULT_CURRENCY) => {
      const numericAmount = Number(amount || 0);
      if (!Number.isFinite(numericAmount)) return 0;
      return numericAmount;
    },
    []
  );

  const formatCurrency = useCallback(
    (amount, fromCurrency = DEFAULT_CURRENCY) => {
      const converted = convertAmount(amount, fromCurrency);
      const normalized = (selectedCurrency || DEFAULT_CURRENCY).toUpperCase();
      const symbol = CURRENCY_SYMBOLS[normalized] || normalized;
      const formattedNumber = new Intl.NumberFormat('en-PH', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(converted);

      return `${symbol}${formattedNumber}`;
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
