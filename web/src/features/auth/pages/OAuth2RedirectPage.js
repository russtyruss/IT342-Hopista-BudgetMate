import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../../shared/context/AuthContext';
import '../styles/Auth.css';

const OAuth2RedirectPage = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { loginWithOAuthToken } = useAuth();

  const token = searchParams.get('token');
  const rawError = searchParams.get('error');
  const [statusMessage, setStatusMessage] = useState('Completing Google sign in...');
  const [errorMessage, setErrorMessage] = useState('');

  const parsedError = useMemo(() => {
    if (!rawError) return '';

    const decodeSafe = (value) => {
      try {
        return decodeURIComponent(value);
      } catch (_) {
        return value;
      }
    };

    const firstPass = decodeSafe(rawError);
    const secondPass = decodeSafe(firstPass);
    return secondPass.replace(/\+/g, ' ').trim();
  }, [rawError]);

  useEffect(() => {
    const run = async () => {
      if (parsedError) {
        setErrorMessage(parsedError || 'Google login failed. Please try again.');
        setStatusMessage('Unable to sign in with Google.');
        return;
      }

      if (!token) {
        setErrorMessage('Google login did not return a valid token. Please try again.');
        setStatusMessage('Unable to sign in with Google.');
        return;
      }

      try {
        await loginWithOAuthToken(token);
        setStatusMessage('Google sign in successful! Redirecting to dashboard...');
        setTimeout(() => navigate('/dashboard', { replace: true }), 600);
      } catch (_) {
        setErrorMessage('Google login succeeded but profile loading failed. Please try logging in again.');
        setStatusMessage('Unable to complete sign in.');
      }
    };

    run();
  }, [loginWithOAuthToken, navigate, parsedError, token]);

  return (
    <div className="auth-container">
      <div className="auth-card">
        <h2>Google Sign In</h2>
        {!errorMessage && <div className="auth-success">{statusMessage}</div>}
        {errorMessage && <div className="auth-error">{errorMessage}</div>}
        {errorMessage && (
          <button
            type="button"
            className="btn-primary"
            onClick={() => navigate('/login', { replace: true, state: { message: errorMessage } })}
          >
            Back to Login
          </button>
        )}
      </div>
    </div>
  );
};

export default OAuth2RedirectPage;
