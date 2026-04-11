import React, { useEffect, useState } from 'react';
import { HiOutlineHandRaised } from 'react-icons/hi2';
import { useAuth } from '../context/AuthContext';
import { useCurrency } from '../context/CurrencyContext';
import { getExpenses } from '../api/expenseApi';
import { getBudgets } from '../api/budgetApi';
import './Dashboard.css';

const DashboardPage = () => {
  const { user } = useAuth();
  const { formatCurrency, convertAmount, selectedCurrency } = useCurrency();
  const [expenses, setExpenses] = useState([]);
  const [budgets, setBudgets] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [expRes, budRes] = await Promise.all([
          getExpenses({ size: 5, sort: 'expenseDate,desc' }),
          getBudgets(),
        ]);
        setExpenses(expRes.data?.content || expRes.data || []);
        setBudgets(budRes.data?.content || budRes.data || []);
      } catch (err) {
        console.error('Failed to load dashboard data', err);
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  const totalExpenses = expenses.reduce(
    (sum, e) => sum + convertAmount(e.amount, e.currency || 'PHP'),
    0
  );
  const totalBudget = budgets.reduce(
    (sum, b) => sum + convertAmount(b.limitAmount, b.currency || 'PHP'),
    0
  );

  if (loading) return <div className="loading">Loading dashboard...</div>;

  return (
    <div className="dashboard">
      <h1>
        Welcome back, {user?.name} <HiOutlineHandRaised style={{ verticalAlign: 'text-bottom' }} aria-hidden="true" />
      </h1>

      <div className="dashboard-cards">
        <div className="stat-card">
          <span className="stat-label">Total Budget</span>
          <span className="stat-value">{formatCurrency(totalBudget, selectedCurrency)}</span>
        </div>
        <div className="stat-card">
          <span className="stat-label">Recent Spending</span>
          <span className="stat-value expense">{formatCurrency(totalExpenses, selectedCurrency)}</span>
        </div>
        <div className="stat-card">
          <span className="stat-label">Active Budgets</span>
          <span className="stat-value">{budgets.length}</span>
        </div>
      </div>

      <div className="dashboard-sections">
        <div className="section">
          <h2>Recent Expenses</h2>
          {expenses.length === 0 ? (
            <p className="empty">No expenses yet.</p>
          ) : (
            <table className="data-table">
              <thead>
                <tr><th>Description</th><th>Category</th><th>Amount</th><th>Date</th></tr>
              </thead>
              <tbody>
                {expenses.map((e) => (
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
                    <span>{b.category}</span>
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
