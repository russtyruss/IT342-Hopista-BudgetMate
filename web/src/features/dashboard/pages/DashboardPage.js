import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { HiOutlineHandRaised } from 'react-icons/hi2';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useAuth } from '../../../shared/context/AuthContext';
import { useCurrency } from '../../../shared/context/CurrencyContext';
import { getExpenses } from '../../expense/api/expenseApi';
import { getBudgets } from '../../budget/api/budgetApi';
import { getCachedData, setCachedData } from '../../../shared/utils/pageDataCache';
import '../styles/Dashboard.css';

const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080/api/v1';
const WS_HTTP_URL = `${API_BASE_URL.replace(/\/api\/v1\/?$/, '')}/ws`;

const SUMMARY_PERIOD = {
  MONTH: 'MONTH',
  YEAR: 'YEAR',
};

const SUMMARY_METRIC = {
  BUDGET: 'BUDGET',
  SPENT: 'SPENT',
};

const getBudgetRecencyKey = (budget) => budget.createdAt || budget.updatedAt || budget.startDate || '';

const DashboardPage = () => {
  const { user } = useAuth();
  const { formatCurrency, convertAmount, selectedCurrency } = useCurrency();
  const [expenses, setExpenses] = useState([]);
  const [budgets, setBudgets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [budgetSummaryPeriod, setBudgetSummaryPeriod] = useState(SUMMARY_PERIOD.MONTH);
  const [spentSummaryPeriod, setSpentSummaryPeriod] = useState(SUMMARY_PERIOD.MONTH);
  const [periodSelectorMetric, setPeriodSelectorMetric] = useState(null);
  const refreshTimeoutRef = useRef(null);

  const fetchData = useCallback(async (showLoading = false) => {
    if (showLoading) {
      setLoading(true);
    }

    try {
      const [expRes, budRes] = await Promise.all([
        getExpenses({ size: 200, sort: 'expenseDate,desc' }),
        getBudgets(),
      ]);
      const nextExpenses = expRes.data?.content || expRes.data || [];
      const nextBudgets = budRes.data?.content || budRes.data || [];
      setExpenses(nextExpenses);
      setBudgets(nextBudgets);
      setCachedData('dashboard:expenses', nextExpenses);
      setCachedData('budgets:list', nextBudgets);
    } catch (err) {
      console.error('Failed to load dashboard data', err);
    } finally {
      if (showLoading) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    const cachedDashboardExpenses = getCachedData('dashboard:expenses') || getCachedData('expenses:list');
    const cachedBudgets = getCachedData('budgets:list');
    const hasCachedData = Boolean(cachedDashboardExpenses || cachedBudgets);
    if (cachedDashboardExpenses) {
      setExpenses(cachedDashboardExpenses);
      setLoading(false);
    }
    if (cachedBudgets) {
      setBudgets(cachedBudgets);
    }

    fetchData(!hasCachedData);
  }, [fetchData]);

  useEffect(() => {
    if (!user?.id) {
      return undefined;
    }

    const stompClient = new Client({
      webSocketFactory: () => new SockJS(WS_HTTP_URL),
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => {},
    });

    stompClient.onConnect = () => {
      stompClient.subscribe(`/topic/dashboard/${user.id}`, () => {
        // Coalesce rapid events into one refresh burst.
        if (refreshTimeoutRef.current) {
          return;
        }

        refreshTimeoutRef.current = setTimeout(() => {
          refreshTimeoutRef.current = null;
          fetchData(false);
        }, 350);
      });
    };

    stompClient.onWebSocketError = () => {
      // Keep failures non-blocking; dashboard can still refresh via normal API usage.
    };

    stompClient.activate();

    return () => {
      if (refreshTimeoutRef.current) {
        clearTimeout(refreshTimeoutRef.current);
        refreshTimeoutRef.current = null;
      }
      stompClient.deactivate();
    };
  }, [user?.id, fetchData]);

  const extractYearMonth = (rawDate) => {
    if (!rawDate || typeof rawDate !== 'string') {
      return null;
    }

    const normalized = rawDate.trim().replace(/\//g, '-');
    const parts = normalized.split('-');
    if (parts.length < 2) {
      return null;
    }

    const year = Number(parts[0]);
    const month = Number((parts[1] || '').slice(0, 2));
    if (!Number.isInteger(year) || !Number.isInteger(month) || month < 1 || month > 12) {
      return null;
    }
    return { year, month };
  };

  const now = new Date();
  const currentYear = now.getFullYear();
  const currentMonth = now.getMonth() + 1;

  const isInSelectedPeriod = (rawDate, period) => {
    const parsed = extractYearMonth(rawDate);
    if (!parsed) {
      return false;
    }

    if (period === SUMMARY_PERIOD.YEAR) {
      return parsed.year === currentYear;
    }

    return parsed.year === currentYear && parsed.month === currentMonth;
  };

  const summaryExpenses = expenses.filter((expense) => isInSelectedPeriod(expense.expenseDate || expense.date || expense.createdAt, spentSummaryPeriod));
  const summaryBudgets = budgets.filter((budget) => isInSelectedPeriod(budget.startDate || budget.createdAt || budget.updatedAt, budgetSummaryPeriod));

  const totalExpenses = summaryExpenses.reduce(
    (sum, e) => sum + convertAmount(e.amount, e.currency || 'PHP'),
    0
  );
  const totalBudget = summaryBudgets.reduce(
    (sum, b) => sum + convertAmount(b.limitAmount, b.currency || 'PHP'),
    0
  );
  const budgetPeriodLabel = budgetSummaryPeriod === SUMMARY_PERIOD.MONTH ? 'Month' : 'Year';
  const spentPeriodLabel = spentSummaryPeriod === SUMMARY_PERIOD.MONTH ? 'Month' : 'Year';
  const recentExpenses = expenses.slice(0, 5);
  const activePeriod = periodSelectorMetric === SUMMARY_METRIC.BUDGET ? budgetSummaryPeriod : spentSummaryPeriod;

  const categorySequenceByBudgetId = useMemo(() => {
    const grouped = new Map();

    budgets.forEach((budget) => {
      const categoryKey = (budget.category || '').trim().toLowerCase();
      const current = grouped.get(categoryKey) || [];
      current.push(budget);
      grouped.set(categoryKey, current);
    });

    const sequenceById = {};
    grouped.forEach((groupBudgets) => {
      if (groupBudgets.length <= 1) {
        return;
      }

      const sortedByRecency = [...groupBudgets].sort((a, b) => {
        const aKey = getBudgetRecencyKey(a);
        const bKey = getBudgetRecencyKey(b);
        if (aKey !== bKey) {
          return bKey.localeCompare(aKey);
        }
        return Number(b.id || 0) - Number(a.id || 0);
      });

      sortedByRecency.forEach((budget, index) => {
        sequenceById[budget.id] = index + 1;
      });
    });

    return sequenceById;
  }, [budgets]);

  const formatBudgetCategory = (budget) => {
    const sequence = categorySequenceByBudgetId[budget.id];
    return sequence ? `${budget.category} ${sequence}` : budget.category;
  };

  if (loading) return <div className="loading">Loading dashboard...</div>;

  return (
    <div className="dashboard">
      {periodSelectorMetric && (
        <div className="modal-overlay">
          <div className="modal period-modal">
            <h2>{periodSelectorMetric === SUMMARY_METRIC.BUDGET ? 'Total Budget Period' : 'Total Spent Period'}</h2>
            <div className="period-options" role="group" aria-label="Summary period selector">
              <button
                type="button"
                className={`period-option ${activePeriod === SUMMARY_PERIOD.MONTH ? 'active' : ''}`}
                onClick={() => {
                  if (periodSelectorMetric === SUMMARY_METRIC.BUDGET) {
                    setBudgetSummaryPeriod(SUMMARY_PERIOD.MONTH);
                  } else {
                    setSpentSummaryPeriod(SUMMARY_PERIOD.MONTH);
                  }
                  setPeriodSelectorMetric(null);
                }}
              >
                Total Month
              </button>
              <button
                type="button"
                className={`period-option ${activePeriod === SUMMARY_PERIOD.YEAR ? 'active' : ''}`}
                onClick={() => {
                  if (periodSelectorMetric === SUMMARY_METRIC.BUDGET) {
                    setBudgetSummaryPeriod(SUMMARY_PERIOD.YEAR);
                  } else {
                    setSpentSummaryPeriod(SUMMARY_PERIOD.YEAR);
                  }
                  setPeriodSelectorMetric(null);
                }}
              >
                Total Year
              </button>
            </div>
            <div className="modal-actions">
              <button type="button" className="btn-secondary" onClick={() => setPeriodSelectorMetric(null)}>
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      <h1>
        Welcome back, {user?.name} <HiOutlineHandRaised style={{ verticalAlign: 'text-bottom' }} aria-hidden="true" />
      </h1>

      <div className="dashboard-cards">
        <button type="button" className="stat-card stat-card-button" onClick={() => setPeriodSelectorMetric(SUMMARY_METRIC.BUDGET)}>
          <span className="stat-label">Total Budget ({budgetPeriodLabel})</span>
          <span className="stat-value">{formatCurrency(totalBudget, selectedCurrency)}</span>
        </button>
        <button type="button" className="stat-card stat-card-button" onClick={() => setPeriodSelectorMetric(SUMMARY_METRIC.SPENT)}>
          <span className="stat-label">Total Spent ({spentPeriodLabel})</span>
          <span className="stat-value expense">{formatCurrency(totalExpenses, selectedCurrency)}</span>
        </button>
        <div className="stat-card">
          <span className="stat-label">Active Budgets</span>
          <span className="stat-value">{budgets.length}</span>
        </div>
      </div>

      <div className="dashboard-sections">
        <div className="section">
          <h2>Recent Expenses</h2>
          {recentExpenses.length === 0 ? (
            <p className="empty">No expenses yet.</p>
          ) : (
            <table className="data-table">
              <thead>
                <tr><th>Description</th><th>Category</th><th>Amount</th><th>Date</th></tr>
              </thead>
              <tbody>
                {recentExpenses.map((e) => (
                  <tr key={e.id}>
                    <td>{e.title || e.description}</td>
                    <td><span className="badge">{e.category}</span></td>
                    <td>{formatCurrency(e.amount, e.currency || 'PHP')}</td>
                    <td>{e.expenseDate || e.date}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        <div className="section">
          <h2>Budget Status</h2>
          {budgets.length === 0 ? (
            <p className="empty">No budgets set.</p>
          ) : (
            budgets.map((b) => {
              const limit = Number(b.limitAmount || 0);
              const spent = Number(b.spentAmount || 0);
              const pct = limit ? Math.min((spent / limit) * 100, 100) : 0;
              return (
                <div key={b.id} className="budget-bar-item">
                  <div className="budget-bar-label">
                    <span>{formatBudgetCategory(b)}</span>
                    <span>{formatCurrency(spent, b.currency || 'PHP')} / {formatCurrency(limit, b.currency || 'PHP')}</span>
                  </div>
                  <div className="budget-bar-track">
                    <div
                      className={`budget-bar-fill ${pct >= 90 ? 'danger' : pct >= 70 ? 'warning' : ''}`}
                      style={{ width: `${pct}%` }}
                    />
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
};

export default DashboardPage;
