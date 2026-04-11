import React, { useEffect, useState } from 'react';
import { FiEdit2, FiTrash2 } from 'react-icons/fi';
import { getBudgets, createBudget, updateBudget, deleteBudget } from '../api/budgetApi';
import { useCurrency } from '../context/CurrencyContext';
import './Expenses.css';

const EMPTY_FORM = { category: '', amount: '', startDate: '', endDate: '', notes: '' };

const BudgetsPage = () => {
  const { formatCurrency } = useCurrency();
  const [budgets, setBudgets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [editId, setEditId] = useState(null);
  const [error, setError] = useState('');

  const fetchBudgets = async () => {
    try {
      const res = await getBudgets();
      setBudgets(res.data?.content || res.data || []);
    } catch (err) {
      setError('Failed to load budgets.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchBudgets(); }, []);

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const payload = {
        category: form.category,
        limitAmount: parseFloat(form.amount),
        currency: 'PHP',
        startDate: form.startDate,
        endDate: form.endDate,
        notes: form.notes || '',
      };
      if (editId) {
        await updateBudget(editId, payload);
      } else {
        await createBudget(payload);
      }
      setForm(EMPTY_FORM);
      setEditId(null);
      setShowForm(false);
      fetchBudgets();
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to save budget.');
    }
  };

  const handleEdit = (b) => {
    setForm({
      category: b.category,
      amount: b.limitAmount,
      startDate: b.startDate,
      endDate: b.endDate,
      notes: b.notes || '',
    });
    setEditId(b.id);
    setShowForm(true);
  };

  const handleDelete = async (id) => {
    if (window.confirm('Delete this budget?')) {
      await deleteBudget(id);
      fetchBudgets();
    }
  };

  if (loading) return <div className="loading">Loading budgets...</div>;

  return (
    <div className="page">
      <div className="page-header">
        <h1>Budgets</h1>
        <button className="btn-primary" onClick={() => { setForm(EMPTY_FORM); setEditId(null); setShowForm(true); }}>
          + Add Budget
        </button>
      </div>

      {error && <div className="error-msg">{error}</div>}

      {showForm && (
        <div className="modal-overlay">
          <div className="modal">
            <h2>{editId ? 'Edit Budget' : 'Add Budget'}</h2>
            <form onSubmit={handleSubmit}>
              <div className="form-group">
                <label>Category</label>
                <select name="category" value={form.category} onChange={handleChange} required>
                  <option value="">Select category</option>
                  {['Food','Transport','Shopping','Entertainment','Health','Education','Utilities','Other'].map(c => (
                    <option key={c} value={c}>{c}</option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label>Budget Amount (₱)</label>
                <input name="amount" type="number" step="0.01" min="0" value={form.amount} onChange={handleChange} required />
              </div>
              <div className="form-row">
                <div className="form-group">
                  <label>Start Date</label>
                  <input name="startDate" type="date" value={form.startDate} onChange={handleChange} required />
                </div>
                <div className="form-group">
                  <label>End Date</label>
                  <input name="endDate" type="date" value={form.endDate} onChange={handleChange} required />
                </div>
              </div>
              <div className="form-group">
                <label>Notes (optional)</label>
                <textarea name="notes" value={form.notes} onChange={handleChange} rows={2} />
              </div>
              <div className="modal-actions">
                <button type="button" className="btn-secondary" onClick={() => setShowForm(false)}>Cancel</button>
                <button type="submit" className="btn-primary">{editId ? 'Update' : 'Add'}</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {budgets.length === 0 ? (
        <p className="empty">No budgets set yet. Click "+ Add Budget" to create one.</p>
      ) : (
        <div className="budget-grid">
          {budgets.map((b) => {
            const limit = Number(b.limitAmount || 0);
            const spent = Number(b.spentAmount || 0);
            const pct = limit ? Math.min((spent / limit) * 100, 100) : 0;
            return (
              <div key={b.id} className="budget-card">
                <div className="budget-card-header">
                  <span className="badge">{b.category}</span>
                  <div>
                    <button className="btn-icon" onClick={() => handleEdit(b)} aria-label="Edit budget" title="Edit">
                      <FiEdit2 />
                    </button>
                    <button className="btn-icon danger" onClick={() => handleDelete(b.id)} aria-label="Delete budget" title="Delete">
                      <FiTrash2 />
                    </button>
                  </div>
                </div>
                <div className="budget-amounts">
                  <span>Spent: {formatCurrency(spent, b.currency || 'PHP')}</span>
                  <span>Budget: {formatCurrency(limit, b.currency || 'PHP')}</span>
                </div>
                <div className="budget-bar-track">
                  <div
                    className={`budget-bar-fill ${pct >= 90 ? 'danger' : pct >= 70 ? 'warning' : ''}`}
                    style={{ width: `${pct}%` }}
                  />
                </div>
                <div className="budget-dates">{b.startDate} → {b.endDate}</div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};

export default BudgetsPage;
