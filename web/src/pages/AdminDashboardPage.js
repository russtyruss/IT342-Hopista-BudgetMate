import React, { useEffect, useState } from 'react';
import { FiTrash2 } from 'react-icons/fi';
import { getUsers, deleteUser } from '../api/userApi';
import { getCachedData, setCachedData } from '../utils/pageDataCache';
import './AdminDashboard.css';

const AdminDashboardPage = () => {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const fetchUsers = async () => {
    setError('');
    try {
      const res = await getUsers({ size: 200, sort: 'createdAt,desc' });
      const nextUsers = res.data?.content || res.data || [];
      setUsers(nextUsers);
      setCachedData('admin:users', nextUsers);
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load users.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const cachedUsers = getCachedData('admin:users');
    if (cachedUsers) {
      setUsers(cachedUsers);
      setLoading(false);
    }
    fetchUsers();
  }, []);

  useEffect(() => {
    if (!error) {
      return;
    }

    const clearTimer = setTimeout(() => setError(''), 3000);
    return () => clearTimeout(clearTimer);
  }, [error]);

  const handleDelete = async (user) => {
    const confirmed = window.confirm(`Delete user ${user.name} (${user.email})? This action cannot be undone.`);
    if (!confirmed) {
      return;
    }

    try {
      setUsers((prev) => {
        const next = prev.filter((u) => u.id !== user.id);
        setCachedData('admin:users', next);
        return next;
      });
      await deleteUser(user.id);
      fetchUsers();
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to delete user.');
      fetchUsers();
    }
  };

  if (loading) return <div className="loading">Loading users...</div>;

  return (
    <div className="page">
      <div className="page-header admin-header">
        <div>
          <h1>Admin Dashboard</h1>
          <p className="admin-subtitle">Manage user accounts, activity status, and access.</p>
        </div>
      </div>

      {error && <div className="error-msg">{error}</div>}

      <div className="admin-metrics">
        <div className="admin-metric-card">
          <span>Total Users</span>
          <strong>{users.length}</strong>
        </div>
        <div className="admin-metric-card">
          <span>Active</span>
          <strong>{users.filter((u) => u.status === 'ACTIVE').length}</strong>
        </div>
        <div className="admin-metric-card">
          <span>Inactive</span>
          <strong>{users.filter((u) => u.status !== 'ACTIVE').length}</strong>
        </div>
      </div>

      {users.length === 0 ? (
        <p className="empty">No users found.</p>
      ) : (
        <table className="data-table admin-users-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Email / Username</th>
              <th>Role</th>
              <th>Status</th>
              <th>Last Login</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {users.map((user) => (
              <tr key={user.id}>
                <td>{user.name}</td>
                <td>{user.email}</td>
                <td>
                  <span className={`role-badge ${(user.roles || []).includes('ADMIN') ? 'admin' : 'user'}`}>
                    {(user.roles || []).includes('ADMIN') ? 'Admin' : 'User'}
                  </span>
                </td>
                <td>
                  <span className={`status-badge ${user.status === 'ACTIVE' ? 'active' : 'inactive'}`}>
                    {user.status === 'ACTIVE' ? 'Active' : 'Inactive'}
                  </span>
                </td>
                <td>{user.lastLoginAt ? new Date(user.lastLoginAt).toLocaleString() : 'Never'}</td>
                <td>
                  {(user.roles || []).includes('ADMIN') ? (
                    <span className="admin-locked">Protected</span>
                  ) : (
                    <button
                      className="btn-icon danger"
                      onClick={() => handleDelete(user)}
                      title="Delete user"
                      aria-label={`Delete ${user.name}`}
                    >
                      <FiTrash2 />
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
};

export default AdminDashboardPage;
