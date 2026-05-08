import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const ProtectedRoute = ({ children, adminOnly = false }) => {
  const { user, loading } = useAuth();
  if (loading) return <div className="loading-screen">Loading...</div>;

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  const isAdmin = user?.role === 'ADMIN' || (Array.isArray(user?.roles) && user.roles.includes('ADMIN'));
  if (adminOnly && !isAdmin) {
    return <Navigate to="/dashboard" replace />;
  }

  return children;
};

export default ProtectedRoute;
