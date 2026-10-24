import React, { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useCurrency } from '../context/CurrencyContext';
import './Navbar.css';

const Navbar = () => {
  const { user, logout } = useAuth();
  const { selectedCurrency, setSelectedCurrency, supportedCurrencies } = useCurrency();
  const navigate = useNavigate();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef(null);
  const isAdmin = user?.role === 'ADMIN' || (Array.isArray(user?.roles) && user.roles.includes('ADMIN'));

  useEffect(() => {
    setMenuOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!menuOpen) return undefined;

    const closeOnOutsideClick = (event) => {
      if (!menuRef.current?.contains(event.target)) setMenuOpen(false);
    };
    const closeOnEscape = (event) => {
      if (event.key === 'Escape') setMenuOpen(false);
    };

    document.addEventListener('pointerdown', closeOnOutsideClick);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('pointerdown', closeOnOutsideClick);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [menuOpen]);

  const handleLogout = async () => {
    setMenuOpen(false);
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
          <Link to="/dashboard">Dashboard</Link>
          <Link to="/expenses">Expenses</Link>
          <Link to="/budgets">Budgets</Link>
          <div className="desktop-nav-controls">
            <Link to="/profile">Profile</Link>
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
          <div className="mobile-nav-menu" ref={menuRef}>
            <button
              type="button"
              className="mobile-menu-toggle"
              aria-expanded={menuOpen}
              aria-controls="mobile-nav-options"
              onClick={() => setMenuOpen((open) => !open)}
            >
              More <span aria-hidden="true">▾</span>
            </button>
            <div id="mobile-nav-options" className={`mobile-menu-panel${menuOpen ? ' open' : ''}`}>
              <div className="currency-switcher">
                <label htmlFor="mobileDisplayCurrency">Currency</label>
                <select
                  id="mobileDisplayCurrency"
                  value={selectedCurrency}
                  onChange={(e) => setSelectedCurrency(e.target.value)}
                >
                  {supportedCurrencies.map((code) => (
                    <option key={code} value={code}>{code}</option>
                  ))}
                </select>
              </div>
              {isAdmin && <Link to="/admin" onClick={() => setMenuOpen(false)}>Admin</Link>}
              <Link to="/profile" onClick={() => setMenuOpen(false)}>Profile</Link>
              <button type="button" className="btn-logout" onClick={handleLogout}>Logout</button>
            </div>
          </div>
        </div>
      )}
    </nav>
  );
};

export default Navbar;
