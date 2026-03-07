import React, { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { getExpenses } from '../api/expenseApi';
import { getBudgets } from '../api/budgetApi';
import './Dashboard.css';

const DashboardPage = () => {
  const { user } = useAuth();
  const [expenses, setExpenses] = useState([]);
  const [budgets, setBudgets] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [expRes, budRes] = await Promise.all([
          getExpenses({ size: 5, sort: 'date,desc' }),
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

  const totalExpenses = expenses.reduce((sum, e) => sum + (e.amount || 0), 0);
  const totalBudget = budgets.reduce((sum, b) => sum + (b.amount || 0), 0);

  if (loading) return <div className="loading">Loading dashboard...</div>;

  return (
    <div className="dashboard">
      <h1>Welcome back, {user?.name} 👋</h1>

      <div className="dashboard-cards">
        <div className="stat-card">
          <span className="stat-label">Total Budget</span>
          <span className="stat-value">₱{totalBudget.toFixed(2)}</span>
        </div>
        <div className="stat-card">
          <span className="stat-label">Recent Spending</span>
          <span className="stat-value expense">₱{totalExpenses.toFixed(2)}</span>
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
                    <td>{e.description}</td>
                    <td><span className="badge">{e.category}</span></td>
                    <td>₱{e.amount?.toFixed(2)}</td>
                    <td>{e.date}</td>
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
              const pct = b.amount ? Math.min((b.spentAmount / b.amount) * 100, 100) : 0;
              return (
                <div key={b.id} className="budget-bar-item">
                  <div className="budget-bar-label">
                    <span>{b.category}</span>
                    <span>₱{b.spentAmount?.toFixed(2)} / ₱{b.amount?.toFixed(2)}</span>
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
