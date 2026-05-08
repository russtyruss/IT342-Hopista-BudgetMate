import React, { useState, useEffect } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { resetPassword } from '../../../shared/api/authApi';
import '../styles/Auth.css';

const ResetPasswordPage = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [form, setForm] = useState({ token: '', newPassword: '' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  useEffect(() => {
    const token = searchParams.get('token');
    if (token) setForm((f) => ({ ...f, token }));
  }, [searchParams]);

  useEffect(() => {
    if (!error) {
      return;
    }

    const clearTimer = setTimeout(() => setError(''), 3000);
    return () => clearTimeout(clearTimer);
  }, [error]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await resetPassword(form);
      setSuccess(true);
      setTimeout(() => navigate('/login'), 3000);
    } catch (err) {
      setError(err.response?.data?.message || 'Reset failed. Link may have expired.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-container">
      <div className="auth-card">
        <h2>💰 BudgetMate</h2>
        <h3>Reset Password</h3>
        {success ? (
          <div className="auth-success">
            Password reset successfully! Redirecting to login...
          </div>
        ) : (
          <>
            {error && <div className="auth-error">{error}</div>}
            <form onSubmit={handleSubmit}>
              <div className="form-group">
                <label>New Password</label>
                <input
                  type="password"
                  value={form.newPassword}
                  onChange={(e) => setForm({ ...form, newPassword: e.target.value })}
                  placeholder="Min 8 chars, upper, lower, number, special"
                  required
                />
                <small>Must include uppercase, lowercase, number, and special character.</small>
              </div>
              <button type="submit" className="btn-primary" disabled={loading}>
                {loading ? 'Resetting...' : 'Reset Password'}
              </button>
            </form>
            <p className="auth-switch"><Link to="/login">Back to Sign In</Link></p>
          </>
        )}
      </div>
    </div>
  );
};

export default ResetPasswordPage;
