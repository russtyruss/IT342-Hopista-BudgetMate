import React, { useEffect, useMemo, useState } from 'react';
import { FiEdit2, FiTrash2 } from 'react-icons/fi';
import { getBudgets, createBudget, updateBudget, deleteBudget } from '../api/budgetApi';
import { getExpenses } from '../../expense/api/expenseApi';
import { useCurrency } from '../../../shared/context/CurrencyContext';
import { getCachedData, setCachedData } from '../../../shared/utils/pageDataCache';
import '../../../shared/styles/Expenses.css';

const BUDGET_CATEGORIES = ['Food', 'Transport', 'Shopping', 'Entertainment', 'Health', 'Education', 'Utilities', 'Other'];
const EMPTY_FORM = { category: '', customCategory: '', amount: '', startDate: '', endDate: '', notes: '', hasSchedule: false };

const getRecencyKey = (budget) => budget.createdAt || budget.updatedAt || budget.startDate || '';

const BudgetsPage = () => {
  const { formatCurrency } = useCurrency();
  const [budgets, setBudgets] = useState([]);
  const [expenses, setExpenses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [showLinkedExpenses, setShowLinkedExpenses] = useState(false);
  const [selectedBudget, setSelectedBudget] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [editId, setEditId] = useState(null);
  const [error, setError] = useState('');
  const [errorVisible, setErrorVisible] = useState(false);

  const fetchBudgets = async () => {
    try {
      const res = await getBudgets();
      const nextBudgets = res.data?.content || res.data || [];
      setBudgets(nextBudgets);
      setCachedData('budgets:list', nextBudgets);
    } catch (err) {
      setError('Failed to load budgets.');
    } finally {
      setLoading(false);
    }
  };

  const fetchExpenses = async () => {
    try {
      const res = await getExpenses({ sort: 'expenseDate,desc', size: 200 });
      const nextExpenses = res.data?.content || res.data || [];
      setExpenses(nextExpenses);
      setCachedData('expenses:list', nextExpenses);
    } catch (_) {
      setExpenses([]);
    }
  };

  useEffect(() => {
    const cachedBudgets = getCachedData('budgets:list');
    const cachedExpenses = getCachedData('expenses:list');
    if (cachedBudgets) {
      setBudgets(cachedBudgets);
      setLoading(false);
    }
    if (cachedExpenses) {
      setExpenses(cachedExpenses);
    }

    fetchBudgets();
    fetchExpenses();
  }, []);

  useEffect(() => {
    if (!error) {
      setErrorVisible(false);
      return;
    }

    setErrorVisible(true);
    const fadeTimer = setTimeout(() => setErrorVisible(false), 2500);
    const clearTimer = setTimeout(() => setError(''), 3000);

    return () => {
      clearTimeout(fadeTimer);
      clearTimeout(clearTimer);
    };
  }, [error]);

  useEffect(() => {
    if (!selectedBudget) {
      return;
    }

    const stillExists = budgets.some((budget) => Number(budget.id) === Number(selectedBudget.id));
    if (!stillExists) {
      setShowLinkedExpenses(false);
      setSelectedBudget(null);
    }
  }, [budgets, selectedBudget]);

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const hasSchedule = Boolean(form.hasSchedule);
      const amount = Number(form.amount);
      const category = form.category === 'Other' ? form.customCategory.trim() : form.category.trim();

      if (!category) {
        setError('Please provide a budget category.');
        return;
      }

      if (!Number.isFinite(amount) || amount <= 0) {
        const message = 'Amount is invalid. Please enter a value greater than 0.';
        setError(message);
        window.alert(message);
        return;
      }

      if (hasSchedule && (!form.startDate || !form.endDate)) {
        setError('Please provide both start and end dates for scheduled budgets.');
        return;
      }

      if (hasSchedule && form.endDate < form.startDate) {
        setError('End date must be after start date.');
        return;
      }

      const payload = {
        category,
        limitAmount: amount,
        currency: 'PHP',
        startDate: hasSchedule ? form.startDate : null,
        endDate: hasSchedule ? form.endDate : null,
        notes: form.notes?.trim() || '',
      };
      if (editId) {
        const res = await updateBudget(editId, payload);
        const updatedBudget = res.data;
        setBudgets((prev) => {
          const next = prev.map((budget) =>
            Number(budget.id) === Number(editId) ? updatedBudget : budget
          );
          setCachedData('budgets:list', next);
          return next;
        });
      } else {
        const res = await createBudget(payload);
        const createdBudget = res.data;
        setBudgets((prev) => {
          const next = [createdBudget, ...prev];
          setCachedData('budgets:list', next);
          return next;
        });
      }
      setForm(EMPTY_FORM);
      setEditId(null);
      setShowForm(false);
      fetchBudgets();
      fetchExpenses();
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to save budget.');
    }
  };

  const handleEdit = (b) => {
    const categoryExists = BUDGET_CATEGORIES.includes(b.category);
    setForm({
      category: categoryExists ? b.category : 'Other',
      customCategory: categoryExists ? '' : b.category,
      amount: b.limitAmount,
      startDate: b.startDate || '',
      endDate: b.endDate || '',
      notes: b.notes || '',
      hasSchedule: Boolean(b.startDate && b.endDate),
    });
    setEditId(b.id);
    setShowForm(true);
  };

  const handleDelete = async (id) => {
    if (window.confirm('Delete this budget? All expenses linked to this budget will also be deleted.')) {
      setShowLinkedExpenses(false);
      setSelectedBudget(null);
      setBudgets((prev) => {
        const next = prev.filter((budget) => Number(budget.id) !== Number(id));
        setCachedData('budgets:list', next);
        return next;
      });
      await deleteBudget(id);
      fetchBudgets();
      fetchExpenses();
    }
  };

  const openLinkedExpenses = (budget) => {
    setSelectedBudget(budget);
    setShowLinkedExpenses(true);
  };

  const linkedExpenses = selectedBudget
    ? expenses.filter((expense) => Number(expense.budgetId) === Number(selectedBudget.id))
    : [];

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
        const aKey = getRecencyKey(a);
        const bKey = getRecencyKey(b);
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

  if (loading) return <div className="loading">Loading budgets...</div>;

  return (
    <div className="page">
      <div className="page-header">
        <h1>Budgets</h1>
        <button className="btn-primary" onClick={() => { setForm(EMPTY_FORM); setEditId(null); setShowForm(true); }}>
          + Add Budget
        </button>
      </div>

      {error && <div className={`error-msg ${errorVisible ? 'show' : 'hide'}`}>{error}</div>}

      {showForm && (
        <div className="modal-overlay">
          <div className="modal">
            <h2>{editId ? 'Edit Budget' : 'Add Budget'}</h2>
            <form onSubmit={handleSubmit}>
              <div className="form-group">
                <label>Category</label>
                <select name="category" value={form.category} onChange={handleChange} required>
                  <option value="">Select category</option>
                  {BUDGET_CATEGORIES.map(c => (
                    <option key={c} value={c}>{c}</option>
                  ))}
                </select>
              </div>
              {form.category === 'Other' && (
                <div className="form-group">
                  <label>Custom Category</label>
                  <input
                    name="customCategory"
                    value={form.customCategory}
                    onChange={handleChange}
                    placeholder="Type your category"
                    required
                  />
                </div>
              )}
              <div className="form-group">
                <label>Budget Amount (₱)</label>
                <input name="amount" type="number" step="0.01" min="0" value={form.amount} onChange={handleChange} required />
              </div>
              <div className="form-row">
                <div className="form-group">
                  <label>Schedule Type</label>
                  <div className="budget-link-toggle" role="group" aria-label="Budget schedule toggle">
                    <button
                      type="button"
                      className={`toggle-chip ${!form.hasSchedule ? 'active' : ''}`}
                      onClick={() => setForm((prev) => ({ ...prev, hasSchedule: false, startDate: '', endDate: '' }))}
                    >
                      Flexible
                    </button>
                    <button
                      type="button"
                      className={`toggle-chip ${form.hasSchedule ? 'active' : ''}`}
                      onClick={() => setForm((prev) => ({ ...prev, hasSchedule: true }))}
                    >
                      Scheduled
                    </button>
                  </div>
                </div>
              </div>

              {form.hasSchedule && (
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
              )}
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
              <div key={b.id} className="budget-card budget-clickable" onClick={() => openLinkedExpenses(b)}>
                <div className="budget-card-header">
                  <span className="badge">{formatBudgetCategory(b)}</span>
                  <div>
                    <button className="btn-icon" onClick={(e) => { e.stopPropagation(); handleEdit(b); }} aria-label="Edit budget" title="Edit">
                      <FiEdit2 />
                    </button>
                    <button className="btn-icon danger" onClick={(e) => { e.stopPropagation(); handleDelete(b.id); }} aria-label="Delete budget" title="Delete">
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
                <div className="budget-dates">
                  <span>Start: {b.startDate || '-'}</span>
                  <span>End: {b.endDate || '-'}</span>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {showLinkedExpenses && selectedBudget && (
        <div className="modal-overlay">
          <div className="modal detail-sheet">
            <h2>Linked Expenses: {formatBudgetCategory(selectedBudget)}</h2>
            {selectedBudget.notes && (
              <div className="detail-note-block">
                <span className="detail-label">Budget Notes</span>
                <p>{selectedBudget.notes}</p>
              </div>
            )}
            {linkedExpenses.length === 0 ? (
              <p className="empty">No expenses are linked to this budget yet.</p>
            ) : (
              <div className="linked-expenses-list">
                {linkedExpenses.map((expense) => (
                  <div key={expense.id} className="linked-expense-item">
                    <div>
                      <strong>{expense.title || expense.description || 'Expense'}</strong>
                      <p>{expense.expenseDate || expense.date || '-'}</p>
                    </div>
                    <span>{formatCurrency(expense.amount || 0, expense.currency || 'PHP')}</span>
                  </div>
                ))}
              </div>
            )}
            <div className="modal-actions">
              <button type="button" className="btn-secondary" onClick={() => { setShowLinkedExpenses(false); setSelectedBudget(null); }}>
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default BudgetsPage;
