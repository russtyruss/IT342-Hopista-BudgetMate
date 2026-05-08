import React, { useEffect, useMemo, useState } from 'react';
import { useAuth } from '../../../shared/context/AuthContext';
import {
  changeMyPassword,
  downloadMyProfileImage,
  updateMyName,
  uploadMyProfileImage,
} from '../../../shared/api/userApi';
import { getCachedData, setCachedData } from '../../../shared/utils/pageDataCache';
import '../styles/ProfilePage.css';

const ProfilePage = () => {
  const { user, refreshUser, patchUser } = useAuth();
  const [name, setName] = useState(user?.name || '');
  const [selectedFile, setSelectedFile] = useState(null);
  const [showUploadModal, setShowUploadModal] = useState(false);
  const [previewUrl, setPreviewUrl] = useState('');
  const [previewType, setPreviewType] = useState('');
  const [imageRefreshKey, setImageRefreshKey] = useState(Date.now());
  const [loadingPreview, setLoadingPreview] = useState(false);
  const [savingName, setSavingName] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [changingPassword, setChangingPassword] = useState(false);
  const [passwordForm, setPasswordForm] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  });
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    setName(user?.name || '');
  }, [user?.name]);

  useEffect(() => {
    if (!error && !success) {
      return;
    }

    const clearTimer = setTimeout(() => {
      setError('');
      setSuccess('');
    }, 3000);

    return () => clearTimeout(clearTimer);
  }, [error, success]);

  useEffect(() => {
    let objectUrl = '';

    const withCacheBuster = (url, key) => {
      const separator = url.includes('?') ? '&' : '?';
      return `${url}${separator}t=${key}`;
    };

    const fetchPreview = async () => {
      if (!user?.imageUrl) {
        setPreviewUrl('');
        setPreviewType('');
        return;
      }

      if (user.imageUrl.startsWith('http://') || user.imageUrl.startsWith('https://')) {
        setPreviewUrl(withCacheBuster(user.imageUrl, imageRefreshKey));
        setPreviewType('image');
        return;
      }

      const cacheKey = `profile:image:${user.id}:${user.imageUrl}`;
      const cachedPreview = getCachedData(cacheKey);
      if (cachedPreview?.blob) {
        objectUrl = URL.createObjectURL(cachedPreview.blob);
        setPreviewUrl(objectUrl);
        setPreviewType(cachedPreview.type || 'image');
      }

      try {
        setLoadingPreview(true);
        const res = await downloadMyProfileImage(imageRefreshKey);
        const contentType = res.headers['content-type'] || '';
        setCachedData(cacheKey, { blob: res.data, type: contentType }, 120_000);
        if (objectUrl) {
          URL.revokeObjectURL(objectUrl);
        }
        objectUrl = URL.createObjectURL(res.data);
        setPreviewUrl(objectUrl);
        setPreviewType(contentType.startsWith('image/') ? 'image' : 'file');
      } catch (_) {
        setPreviewUrl('');
        setPreviewType('');
      } finally {
        setLoadingPreview(false);
      }
    };

    fetchPreview();

    return () => {
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [user?.id, user?.imageUrl, imageRefreshKey]);

  const hasNameChange = useMemo(() => {
    const trimmedName = (name || '').trim();
    return trimmedName.length >= 2 && trimmedName !== (user?.name || '');
  }, [name, user?.name]);

  const handleNameSave = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    try {
      setSavingName(true);
      const res = await updateMyName({ name: name.trim() });
      patchUser({ name: res.data?.name || name.trim() });
      setSuccess('Display name updated.');
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to update display name.');
    } finally {
      setSavingName(false);
    }
  };

  const handleUpload = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    if (!selectedFile) {
      setError('Please choose an image file first.');
      return;
    }

    try {
      setUploading(true);
      const res = await uploadMyProfileImage(selectedFile);
      patchUser({ imageUrl: res.data?.imageUrl || user?.imageUrl || null });
      await refreshUser();
      setImageRefreshKey(Date.now());
      setSelectedFile(null);
      setShowUploadModal(false);
      setSuccess('Profile picture uploaded successfully.');
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to upload profile picture.');
    } finally {
      setUploading(false);
    }
  };

  const handlePasswordChange = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      setError('New password and confirm password do not match.');
      return;
    }

    try {
      setChangingPassword(true);
      await changeMyPassword({
        currentPassword: passwordForm.currentPassword,
        newPassword: passwordForm.newPassword,
      });
      setPasswordForm({ currentPassword: '', newPassword: '', confirmPassword: '' });
      setSuccess('Password updated successfully.');
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to update password.');
    } finally {
      setChangingPassword(false);
    }
  };

  return (
    <div className="page profile-page">
      <div className="page-header">
        <h1>Profile Settings</h1>
      </div>

      {error && <div className="error-msg">{error}</div>}
      {success && <div className="profile-success">{success}</div>}

      <section className="profile-card">
        <h2>Profile</h2>
        <div className="profile-main-layout">
          <div className="profile-picture-column">
            <div className="profile-preview">
              {loadingPreview && <span>Loading image...</span>}
              {!loadingPreview && previewType === 'image' && previewUrl && (
                <img src={previewUrl} alt="Profile" />
              )}
              {!loadingPreview && !previewUrl && (
                <span>{user?.name?.charAt(0)?.toUpperCase() || 'U'}</span>
              )}
            </div>

            <button
              className="btn-primary profile-upload-trigger"
              type="button"
              onClick={() => {
                setSelectedFile(null);
                setShowUploadModal(true);
              }}
            >
              Upload Picture
            </button>

            {previewUrl && (
              <a className="profile-download-link" href={previewUrl} download="profile-image">
                Download current image
              </a>
            )}
          </div>

          <div className="profile-name-column">
            <form onSubmit={handleNameSave}>
              <div className="form-group">
                <label htmlFor="name">Name</label>
                <input
                  id="name"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  maxLength={100}
                />
              </div>
              <button className="btn-primary" disabled={!hasNameChange || savingName}>
                {savingName ? 'Saving...' : 'Save Name'}
              </button>
            </form>
          </div>
        </div>
      </section>

      {showUploadModal && (
        <div className="profile-upload-modal-backdrop" role="dialog" aria-modal="true">
          <div className="profile-upload-modal">
            <h3>Upload Profile Picture</h3>
            <form onSubmit={handleUpload}>
              <div className="form-group">
                <label htmlFor="profileImage">Choose image</label>
                <input
                  id="profileImage"
                  type="file"
                  accept="image/*"
                  onChange={(e) => setSelectedFile(e.target.files?.[0] || null)}
                />
                <small>Allowed: JPG, PNG, WEBP, GIF. Max size: 5MB.</small>
              </div>
              <div className="profile-upload-actions">
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => {
                    setShowUploadModal(false);
                    setSelectedFile(null);
                  }}
                >
                  Cancel
                </button>
                <button className="btn-primary" disabled={uploading}>
                  {uploading ? 'Uploading...' : 'Upload'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      <section className="profile-card">
        <h2>Change Password</h2>
        <form onSubmit={handlePasswordChange}>
          <div className="form-group">
            <label htmlFor="currentPassword">Current Password</label>
            <input
              id="currentPassword"
              type="password"
              value={passwordForm.currentPassword}
              onChange={(e) => setPasswordForm({ ...passwordForm, currentPassword: e.target.value })}
              required
            />
          </div>

          <div className="form-group">
            <label htmlFor="newPassword">New Password</label>
            <input
              id="newPassword"
              type="password"
              value={passwordForm.newPassword}
              onChange={(e) => setPasswordForm({ ...passwordForm, newPassword: e.target.value })}
              required
            />
          </div>

          <div className="form-group">
            <label htmlFor="confirmPassword">Confirm New Password</label>
            <input
              id="confirmPassword"
              type="password"
              value={passwordForm.confirmPassword}
              onChange={(e) => setPasswordForm({ ...passwordForm, confirmPassword: e.target.value })}
              required
            />
          </div>

          <button className="btn-primary" disabled={changingPassword}>
            {changingPassword ? 'Updating...' : 'Update Password'}
          </button>
        </form>
      </section>
    </div>
  );
};

export default ProfilePage;
