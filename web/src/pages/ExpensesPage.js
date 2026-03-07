import React, { useEffect, useState } from 'react';
import { getExpenses, createExpense, updateExpense, deleteExpense } from '../api/expenseApi';
import './Expenses.css';

const EMPTY_FORM = { description: '', amount: '', category: '', date: '', notes: '' };

const ExpensesPage = () => {
  const [expenses, setExpenses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [editId, setEditId] = useState(null);
  const [error, setError] = useState('');

  const fetchExpenses = async () => {
    try {
      const res = await getExpenses({ sort: 'date,desc', size: 50 });
      setExpenses(res.data?.content || res.data || []);
    } catch (err) {
      setError('Failed to load expenses.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchExpenses(); }, []);

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const payload = { ...form, amount: parseFloat(form.amount) };
      if (editId) {
        await updateExpense(editId, payload);
      } else {
        await createExpense(payload);
      }
      setForm(EMPTY_FORM);
      setEditId(null);
      setShowForm(false);
      fetchExpenses();
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to save expense.');
    }
  };

  const handleEdit = (exp) => {
    setForm({ description: exp.description, amount: exp.amount, category: exp.category, date: exp.date, notes: exp.notes || '' });
    setEditId(exp.id);
    setShowForm(true);
  };

  const handleDelete = async (id) => {
    if (window.confirm('Delete this expense?')) {
      await deleteExpense(id);
      fetchExpenses();
    }
  };

  if (loading) return <div className="loading">Loading expenses...</div>;

  return (
    <div className="page">
      <div className="page-header">
        <h1>Expenses</h1>
        <button className="btn-primary" onClick={() => { setForm(EMPTY_FORM); setEditId(null); setShowForm(true); }}>
          + Add Expense
        </button>
      </div>

      {error && <div className="error-msg">{error}</div>}

      {showForm && (
        <div className="modal-overlay">
          <div className="modal">
            <h2>{editId ? 'Edit Expense' : 'Add Expense'}</h2>
            <form onSubmit={handleSubmit}>
              <div className="form-group">
                <label>Description</label>
                <input name="description" value={form.description} onChange={handleChange} required />
              </div>
              <div className="form-row">
                <div className="form-group">
                  <label>Amount (₱)</label>
                  <input name="amount" type="number" step="0.01" min="0" value={form.amount} onChange={handleChange} required />
                </div>
                <div className="form-group">
                  <label>Date</label>
                  <input name="date" type="date" value={form.date} onChange={handleChange} required />
                </div>
              </div>
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

      {expenses.length === 0 ? (
        <p className="empty">No expenses recorded yet. Click "+ Add Expense" to start.</p>
      ) : (
        <table className="data-table">
          <thead>
            <tr><th>Description</th><th>Category</th><th>Amount</th><th>Date</th><th>Actions</th></tr>
          </thead>
          <tbody>
            {expenses.map((e) => (
              <tr key={e.id}>
                <td>{e.description}</td>
                <td><span className="badge">{e.category}</span></td>
                <td>₱{e.amount?.toFixed(2)}</td>
                <td>{e.date}</td>
                <td>
                  <button className="btn-icon" onClick={() => handleEdit(e)}>✏️</button>
                  <button className="btn-icon danger" onClick={() => handleDelete(e.id)}>🗑️</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
};

export default ExpensesPage;
