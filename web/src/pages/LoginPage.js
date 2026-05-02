import React, { useEffect, useState } from 'react';
import { Link, Navigate, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import './Auth.css';

const LoginPage = () => {
  const { login, user, loading: authLoading } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [form, setForm] = useState({ email: '', password: '' });
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(location.state?.message || '');
  const [loading, setLoading] = useState(false);
  const apiBase = process.env.REACT_APP_API_URL || 'http://localhost:8080/api/v1';
  const oauthLoginUrl = `${apiBase.replace(/\/api\/v1\/?$/, '')}/oauth2/authorization/google`;

  useEffect(() => {
    if (!success && !error) {
      return;
    }

    const clearTimer = setTimeout(() => {
      setSuccess('');
      setError('');
    }, 3000);

    return () => clearTimeout(clearTimer);
  }, [success, error]);

  if (!authLoading && user) {
    return <Navigate to="/dashboard" replace />;
  }

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await login(form.email, form.password);
      setSuccess('Login successful! Redirecting...');
      setTimeout(() => navigate('/dashboard'), 1000);
    } catch (err) {
      if (!err.response) {
        setError('Cannot reach server. Make sure backend is running on http://localhost:8080.');
      } else {
        setError(err.response?.data?.message || 'Invalid email or password');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-container">
      <div className="auth-card">
        <h2 className="auth-brand">
          <img src="/budgetmate-logo.png" alt="BudgetMate" className="auth-brand-logo" />
          <span>BudgetMate</span>
        </h2>
        {success && <div className="auth-success">{success}</div>}
        {error && <div className="auth-error">{error}</div>}
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label>Email or Username</label>
            <input
              type="text"
              name="email"
              value={form.email}
              onChange={handleChange}
              placeholder="you@example.com or admin"
              required
            />
          </div>
          <div className="form-group">
            <label>Password</label>
            <input
              type="password"
              name="password"
              value={form.password}
              onChange={handleChange}
              placeholder="••••••••"
              required
            />
          </div>
          <div className="auth-forgot">
            <Link to="/forgot-password">Forgot password?</Link>
          </div>
          <button type="submit" className="btn-primary" disabled={loading}>
            {loading ? 'Signing in...' : 'Sign In'}
          </button>
        </form>
        <div className="auth-divider">or</div>
        <a href={oauthLoginUrl} className="btn-google">
          <img src="https://www.gstatic.com/firebasejs/ui/2.0.0/images/auth/google.svg" alt="Google" />
          Continue with Google
        </a>
        <p className="auth-switch">
          Don't have an account? <Link to="/register">Sign up</Link>
        </p>
      </div>
    </div>
  );
};

export default LoginPage;
