import React, { useEffect, useMemo, useState } from 'react';
import { FiEdit2, FiTrash2 } from 'react-icons/fi';
import { getExpenses, createExpense, updateExpense, deleteExpense } from '../api/expenseApi';
import { getBudgets } from '../../budget/api/budgetApi';
import { useCurrency } from '../../../shared/context/CurrencyContext';
import { getCachedData, setCachedData } from '../../../shared/utils/pageDataCache';
import '../../../shared/styles/Expenses.css';

const EXPENSE_CATEGORIES = ['Food','Transport','Shopping','Entertainment','Health','Education','Utilities','Other'];
const EMPTY_FORM = { description: '', amount: '', category: '', customCategory: '', date: '', notes: '', budgetId: '' };
const getBudgetRecencyKey = (budget) => budget.createdAt || budget.updatedAt || budget.startDate || '';

const ExpensesPage = () => {
  const { formatCurrency } = useCurrency();
  const [expenses, setExpenses] = useState([]);
  const [budgets, setBudgets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [showDetails, setShowDetails] = useState(false);
  const [selectedExpense, setSelectedExpense] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [linkToBudget, setLinkToBudget] = useState(false);
  const [editId, setEditId] = useState(null);
  const [error, setError] = useState('');
  const [errorVisible, setErrorVisible] = useState(false);

  const fetchExpenses = async () => {
    try {
      const res = await getExpenses({ sort: 'expenseDate,desc', size: 50 });
      const nextExpenses = res.data?.content || res.data || [];
      setExpenses(nextExpenses);
      setCachedData('expenses:list', nextExpenses);
    } catch (err) {
      setError('Failed to load expenses.');
    } finally {
      setLoading(false);
    }
  };

  const fetchBudgets = async () => {
    try {
      const res = await getBudgets({ size: 100, sort: 'startDate,desc' });
      const nextBudgets = res.data?.content || res.data || [];
      setBudgets(nextBudgets);
      setCachedData('budgets:list', nextBudgets);
    } catch (err) {
      setBudgets([]);
    }
  };

  useEffect(() => {
    const cachedExpenses = getCachedData('expenses:list');
    const cachedBudgets = getCachedData('budgets:list');
    if (cachedExpenses) {
      setExpenses(cachedExpenses);
      setLoading(false);
    }
    if (cachedBudgets) {
      setBudgets(cachedBudgets);
    }

    fetchExpenses();
    fetchBudgets();
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
    if (!selectedExpense) {
      return;
    }

    const stillExists = expenses.some((expense) => Number(expense.id) === Number(selectedExpense.id));
    if (!stillExists) {
      setShowDetails(false);
      setSelectedExpense(null);
    }
  }, [expenses, selectedExpense]);

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

  const formatBudgetDisplayName = (budget) => {
    if (!budget) {
      return '';
    }

    const sequence = categorySequenceByBudgetId[budget.id];
    return sequence ? `${budget.category} ${sequence}` : budget.category;
  };

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const parsedAmount = parseFloat(form.amount);
      const linkedBudget = linkToBudget && form.budgetId
        ? budgets.find((budget) => Number(budget.id) === Number(form.budgetId))
        : null;

      const resolvedCategory = linkToBudget
        ? linkedBudget?.category
        : (form.category === 'Other' ? form.customCategory?.trim() : form.category?.trim());

      if (!resolvedCategory) {
        setError(linkToBudget ? 'Please select a linked budget.' : 'Please select a category.');
        return;
      }

      if (!Number.isFinite(parsedAmount) || parsedAmount <= 0) {
        const message = 'Amount is invalid. Please enter a value greater than 0.';
        setError(message);
        window.alert(message);
        return;
      }

      if (linkedBudget) {
        const currentSpent = Number(linkedBudget.spentAmount || 0);
        const budgetLimit = Number(linkedBudget.limitAmount || 0);
        const existingExpense = editId
          ? expenses.find((expense) => Number(expense.id) === Number(editId))
          : null;
        const existingAmountOnThisBudget =
          existingExpense && Number(existingExpense.budgetId) === Number(linkedBudget.id)
            ? Number(existingExpense.amount || 0)
            : 0;
        const nextSpent = currentSpent - existingAmountOnThisBudget + parsedAmount;

        if (nextSpent > budgetLimit) {
          const remaining = Math.max(0, budgetLimit - (currentSpent - existingAmountOnThisBudget));
          const message = `Amount exceeds linked budget. Remaining budget: ${formatCurrency(remaining, linkedBudget.currency || 'PHP')}`;
          setError(message);
          window.alert(message);
          return;
        }
      }

      const payload = {
        title: form.description,
        description: form.notes || '',
        amount: parsedAmount,
        currency: 'PHP',
        category: resolvedCategory,
        expenseDate: form.date,
        isRecurring: false,
        budgetId: linkToBudget && form.budgetId ? Number(form.budgetId) : null,
      };
      if (editId) {
        const res = await updateExpense(editId, payload);
        const updatedExpense = res.data;
        setExpenses((prev) => {
          const next = prev.map((expense) =>
            Number(expense.id) === Number(editId) ? updatedExpense : expense
          );
          setCachedData('expenses:list', next);
          return next;
        });
      } else {
        const res = await createExpense(payload);
        const createdExpense = res.data;
        setExpenses((prev) => {
          const next = [createdExpense, ...prev].slice(0, 50);
          setCachedData('expenses:list', next);
          return next;
        });
      }
      setForm(EMPTY_FORM);
      setLinkToBudget(false);
      setEditId(null);
      setShowForm(false);
      fetchExpenses();
      fetchBudgets();
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to save expense.');
    }
  };

  const handleEdit = (exp) => {
    setForm({
      description: exp.title || exp.description || '',
      amount: exp.amount,
      category: EXPENSE_CATEGORIES.includes(exp.category) ? exp.category : 'Other',
      customCategory: EXPENSE_CATEGORIES.includes(exp.category) ? '' : (exp.category || ''),
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
      if (selectedExpense && Number(selectedExpense.id) === Number(id)) {
        setShowDetails(false);
        setSelectedExpense(null);
      }
      setExpenses((prev) => {
        const next = prev.filter((expense) => Number(expense.id) !== Number(id));
        setCachedData('expenses:list', next);
        return next;
      });
      await deleteExpense(id);
      fetchExpenses();
      fetchBudgets();
    }
  };

  const openExpenseDetails = (expense) => {
    setSelectedExpense(expense);
    setShowDetails(true);
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

      {error && <div className={`error-msg ${errorVisible ? 'show' : 'hide'}`}>{error}</div>}

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
                        setForm((prev) => ({ ...prev, budgetId: '' }));
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
                          setForm((prev) => ({ ...prev, budgetId: String(budgets[0].id) }));
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
                          {b.startDate && b.endDate
                            ? `${formatBudgetDisplayName(b)} (${b.startDate} to ${b.endDate})`
                            : `${formatBudgetDisplayName(b)} (Flexible)`}
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
              {!linkToBudget && (
                <>
                  <div className="form-group">
                    <label>Category</label>
                    <select name="category" value={form.category} onChange={handleChange} required>
                      <option value="">Select category</option>
                      {EXPENSE_CATEGORIES.map(c => (
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
                </>
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

      {expenses.length === 0 ? (
        <p className="empty">No expenses recorded yet. Click "+ Add Expense" to start.</p>
      ) : (
        <table className="data-table">
          <thead>
            <tr><th>Description</th><th>Category</th><th>Amount</th><th>Date</th><th>Actions</th></tr>
          </thead>
          <tbody>
            {expenses.map((e) => (
              <tr key={e.id} onClick={() => openExpenseDetails(e)} style={{ cursor: 'pointer' }}>
                <td>{e.title || e.description}</td>
                <td>
                  <span className="badge">
                    {e.budgetId
                      ? formatBudgetDisplayName(budgets.find((budget) => Number(budget.id) === Number(e.budgetId))) || e.category
                      : e.category}
                  </span>
                </td>
                <td>{formatCurrency(e.amount, e.currency || 'PHP')}</td>
                <td>{e.expenseDate || e.date}</td>
                <td>
                  <button className="btn-icon" onClick={(event) => { event.stopPropagation(); handleEdit(e); }} aria-label="Edit expense" title="Edit">
                    <FiEdit2 />
                  </button>
                  <button className="btn-icon danger" onClick={(event) => { event.stopPropagation(); handleDelete(e.id); }} aria-label="Delete expense" title="Delete">
                    <FiTrash2 />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {showDetails && selectedExpense && (
        <div className="modal-overlay">
          <div className="modal detail-sheet">
            <h2>Expense Details</h2>
            <div className="detail-rows">
              <div className="detail-row"><span className="detail-label">Title</span><span className="detail-value">{selectedExpense.title || '-'}</span></div>
              <div className="detail-row"><span className="detail-label">Amount</span><span className="detail-value">{formatCurrency(selectedExpense.amount || 0, selectedExpense.currency || 'PHP')}</span></div>
              <div className="detail-row"><span className="detail-label">Category</span><span className="detail-value">{selectedExpense.category || '-'}</span></div>
              <div className="detail-row"><span className="detail-label">Expense Date</span><span className="detail-value">{selectedExpense.expenseDate || selectedExpense.date || '-'}</span></div>
              {selectedExpense.budgetId && (
                <div className="detail-row">
                  <span className="detail-label">Linked Budget</span>
                  <span className="detail-value">
                    {formatBudgetDisplayName(budgets.find((budget) => Number(budget.id) === Number(selectedExpense.budgetId))) || `#${selectedExpense.budgetId}`}
                  </span>
                </div>
              )}
              {selectedExpense.description && (
                <div className="detail-note-block">
                  <span className="detail-label">Description</span>
                  <p>{selectedExpense.description}</p>
                </div>
              )}
            </div>
            <div className="modal-actions">
              <button type="button" className="btn-secondary" onClick={() => { setShowDetails(false); setSelectedExpense(null); }}>
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ExpensesPage;
