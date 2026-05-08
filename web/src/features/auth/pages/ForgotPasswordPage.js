import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { forgotPassword } from '../../../shared/api/authApi';
import '../styles/Auth.css';

const ForgotPasswordPage = () => {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!error) {
      return;
    }

    const clearTimer = setTimeout(() => setError(''), 3000);
    return () => clearTimeout(clearTimer);
  }, [error]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      await forgotPassword({ email });
      setSent(true);
    } catch (err) {
      setError(err.response?.data?.message || 'Something went wrong. Try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-container">
      <div className="auth-card">
        <h2>💰 BudgetMate</h2>
        <h3>Forgot Password</h3>
        {sent ? (
          <div className="auth-success">
            If that email is registered, a reset link has been sent. Check your inbox.
            <br /><br />
            <Link to="/login">Back to Sign In</Link>
          </div>
        ) : (
          <>
            {error && <div className="auth-error">{error}</div>}
            <p className="auth-hint">Enter your email and we'll send you a reset link.</p>
            <form onSubmit={handleSubmit}>
              <div className="form-group">
                <label>Email</label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@example.com"
                  required
                />
              </div>
              <button type="submit" className="btn-primary" disabled={loading}>
                {loading ? 'Sending...' : 'Send Reset Link'}
              </button>
            </form>
            <p className="auth-switch"><Link to="/login">Back to Sign In</Link></p>
          </>
        )}
      </div>
    </div>
  );
};

export default ForgotPasswordPage;
