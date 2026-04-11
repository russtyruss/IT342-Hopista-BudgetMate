import React, { useEffect, useState } from 'react';
import { FiEdit2, FiTrash2 } from 'react-icons/fi';
import { getExpenses, createExpense, updateExpense, deleteExpense } from '../api/expenseApi';
import { getBudgets } from '../api/budgetApi';
import { useCurrency } from '../context/CurrencyContext';
import './Expenses.css';

const EMPTY_FORM = { description: '', amount: '', category: '', date: '', notes: '', budgetId: '' };

const ExpensesPage = () => {
  const { formatCurrency } = useCurrency();
  const [expenses, setExpenses] = useState([]);
  const [budgets, setBudgets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [linkToBudget, setLinkToBudget] = useState(false);
  const [editId, setEditId] = useState(null);
  const [error, setError] = useState('');

  const fetchExpenses = async () => {
    try {
      const res = await getExpenses({ sort: 'expenseDate,desc', size: 50 });
      setExpenses(res.data?.content || res.data || []);
    } catch (err) {
      setError('Failed to load expenses.');
    } finally {
      setLoading(false);
    }
  };

  const fetchBudgets = async () => {
    try {
      const res = await getBudgets({ size: 100, sort: 'startDate,desc' });
      setBudgets(res.data?.content || res.data || []);
    } catch (err) {
      setBudgets([]);
    }
  };

  useEffect(() => {
    fetchExpenses();
    fetchBudgets();
  }, []);

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const payload = {
        title: form.description,
        description: form.notes || '',
        amount: parseFloat(form.amount),
        currency: 'PHP',
        category: form.category,
        expenseDate: form.date,
        isRecurring: false,
        budgetId: linkToBudget && form.budgetId ? Number(form.budgetId) : null,
      };
      if (editId) {
        await updateExpense(editId, payload);
      } else {
        await createExpense(payload);
      }
      setForm(EMPTY_FORM);
      setLinkToBudget(false);
      setEditId(null);
      setShowForm(false);
      fetchExpenses();
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to save expense.');
    }
  };

  const handleEdit = (exp) => {
    setForm({
      description: exp.title || exp.description || '',
      amount: exp.amount,
      category: exp.category || '',
      date: exp.expenseDate || '',
      notes: exp.description || '',
      budgetId: exp.budgetId ? String(exp.budgetId) : '',
    });
    setLinkToBudget(Boolean(exp.budgetId));
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
        <button className="btn-primary" onClick={() => { setForm(EMPTY_FORM); setLinkToBudget(false); setEditId(null); setShowForm(true); }}>
          + Add Expense
        </button>
      </div>

      {error && <div className="error-msg">{error}</div>}

      {showForm && (
        <div className="modal-overlay">
          <div className="modal">
            <h2>{editId ? 'Edit Expense' : 'Add Expense'}</h2>
            <form onSubmit={handleSubmit}>
              <div className="budget-link-card">
                <div className="budget-link-top">
                  <div>
                    <label className="budget-link-title">Budget Link</label>
                    <p className="budget-link-caption">Choose whether this expense should reduce a specific budget.</p>
                  </div>
                  <div className="budget-link-toggle" role="group" aria-label="Budget link toggle">
                    <button
                      type="button"
                      className={`toggle-chip ${!linkToBudget ? 'active' : ''}`}
                      onClick={() => {
                        setLinkToBudget(false);
                        setForm({ ...form, budgetId: '' });
                      }}
                    >
                      No Link
                    </button>
                    <button
                      type="button"
                      className={`toggle-chip ${linkToBudget ? 'active' : ''}`}
                      onClick={() => {
                        setLinkToBudget(true);
                        if (!form.budgetId && budgets.length > 0) {
                          setForm({ ...form, budgetId: String(budgets[0].id) });
                        }
                      }}
                    >
                      Link Budget
                    </button>
                  </div>
                </div>

                {linkToBudget && (
                  <div className="form-group budget-link-field">
                    <label>Linked Budget</label>
                    <select name="budgetId" value={form.budgetId} onChange={handleChange} required>
                      <option value="">Select budget</option>
                      {budgets.map((b) => (
                        <option key={b.id} value={b.id}>
                          {b.category} ({b.startDate} to {b.endDate})
                        </option>
                      ))}
                    </select>
                  </div>
                )}
              </div>
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
                <td>{e.title || e.description}</td>
                <td><span className="badge">{e.category}</span></td>
                <td>{formatCurrency(e.amount, e.currency || 'PHP')}</td>
                <td>{e.expenseDate || e.date}</td>
                <td>
                  <button className="btn-icon" onClick={() => handleEdit(e)} aria-label="Edit expense" title="Edit">
                    <FiEdit2 />
                  </button>
                  <button className="btn-icon danger" onClick={() => handleDelete(e.id)} aria-label="Delete expense" title="Delete">
                    <FiTrash2 />
                  </button>
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
