import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth as useKeycloakAuth } from 'react-oidc-context';
import { useSnackbar } from '../context/SnackbarContext';
import {
  isRedirectingToLogin,
  markRedirectingToLogin,
  setApiErrorHandlers,
} from './axiosInstance';

/**
 * Bridges axios 401/403 responses to Keycloak + snackbar without importing
 * React context into the axios module.
 */
export const ApiErrorListener = () => {
  const auth = useKeycloakAuth();
  const { showError } = useSnackbar();
  const { t } = useTranslation();

  useEffect(() => {
    setApiErrorHandlers({
      onUnauthorized: () => {
        const path = window.location.pathname;
        if (path.includes('/registration') || path.includes('/login')) {
          return;
        }
        if (isRedirectingToLogin() || auth.activeNavigator) {
          return;
        }
        markRedirectingToLogin();
        const currentPath = path + window.location.search;
        if (currentPath !== '/' && currentPath !== '/login') {
          localStorage.setItem('pendingRedirectPath', currentPath);
        }
        void auth.signinRedirect();
      },
      onForbidden: (detail) => {
        showError(detail || t('errors.forbidden'));
      },
    });
    return () => setApiErrorHandlers(null);
  }, [auth, showError, t]);

  return null;
};
