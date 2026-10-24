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
        <Link to="/dashboard" className="brand-link">
          <img src="/budgetmate-logo.png" alt="BudgetMate" className="brand-logo" />
          <span>BudgetMate</span>
        </Link>
      </div>
      {user && (
        <div className="navbar-links">
          <Link to="/dashboard" className="nav-link">
            <span className="nav-label-full">Dashboard</span>
            <span className="nav-label-mobile">Home</span>
          </Link>
          <Link to="/expenses" className="nav-link">
            <span className="nav-label-full">Expenses</span>
            <span className="nav-label-mobile">Spend</span>
          </Link>
          <Link to="/budgets" className="nav-link">
            <span className="nav-label-full">Budgets</span>
            <span className="nav-label-mobile">Budget</span>
          </Link>
          <Link to="/profile" className="nav-link">
            <span className="nav-label-full">Profile</span>
            <span className="nav-label-mobile">Me</span>
          </Link>
          {isAdmin && (
            <Link to="/admin" className="nav-link">
              <span className="nav-label-full">Admin</span>
              <span className="nav-label-mobile">Admin</span>
            </Link>
          )}
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
