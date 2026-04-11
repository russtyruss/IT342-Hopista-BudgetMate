import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useCurrency } from '../context/CurrencyContext';
import './Navbar.css';

const Navbar = () => {
  const { user, logout } = useAuth();
  const { selectedCurrency, setSelectedCurrency, supportedCurrencies } = useCurrency();
  const navigate = useNavigate();
  const isAdmin = user?.role === 'ADMIN' || (Array.isArray(user?.roles) && user.roles.includes('ADMIN'));

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <nav className="navbar">
      <div className="navbar-brand">
        <Link to="/dashboard">💰 BudgetMate</Link>
      </div>
      {user && (
        <div className="navbar-links">
          <Link to="/dashboard">Dashboard</Link>
          <Link to="/expenses">Expenses</Link>
          <Link to="/budgets">Budgets</Link>
          {isAdmin && <Link to="/admin">Admin</Link>}
          <div className="currency-switcher">
            <label htmlFor="displayCurrency">Currency</label>
            <select
              id="displayCurrency"
              value={selectedCurrency}
              onChange={(e) => setSelectedCurrency(e.target.value)}
            >
              {supportedCurrencies.map((code) => (
                <option key={code} value={code}>{code}</option>
              ))}
            </select>
          </div>
          <span className="navbar-user">Hi, {user.name}</span>
          <button className="btn-logout" onClick={handleLogout}>Logout</button>
        </div>
      )}
    </nav>
  );
};

export default Navbar;
