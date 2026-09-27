import axios, { type AxiosError, type AxiosRequestConfig, type AxiosResponse } from 'axios';
import { getApiBaseUrl } from './apiBase';

export const api = axios.create({
  baseURL: getApiBaseUrl(),
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

export type ApiErrorHandlers = {
  onUnauthorized: () => void;
  onForbidden: (detail?: string) => void;
};

let apiErrorHandlers: ApiErrorHandlers | null = null;
let redirectingToLogin = false;

export const setApiErrorHandlers = (handlers: ApiErrorHandlers | null) => {
  apiErrorHandlers = handlers;
};

const isAuthExemptRequest = (url?: string) => {
  if (!url) return false;
  return url.includes('/api/users/register');
};

const authorizationHeader = (headers: AxiosRequestConfig['headers']): string | undefined => {
  if (!headers) return undefined;
  if (typeof (headers as { get?: unknown }).get === 'function') {
    const value = (headers as { get: (name: string) => unknown }).get('Authorization');
    return typeof value === 'string' ? value : undefined;
  }
  const record = headers as Record<string, unknown>;
  const value = record.Authorization ?? record.authorization;
  return typeof value === 'string' ? value : undefined;
};

const problemDetail = (error: AxiosError): string | undefined => {
  const data = error.response?.data;
  if (data && typeof data === 'object' && 'detail' in data) {
    const detail = (data as { detail?: unknown }).detail;
    return typeof detail === 'string' && detail.trim() ? detail : undefined;
  }
  return undefined;
};

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    const status = error.response?.status;
    const method = (error.config?.method ?? 'get').toLowerCase();
    const url = error.config?.url;

    if (status === 401 && !isAuthExemptRequest(url)) {
      const sentAuthorization = authorizationHeader(error.config?.headers);
      const currentToken = localStorage.getItem('token');
      const sentTheCurrentToken = Boolean(
        sentAuthorization && currentToken && sentAuthorization === `Bearer ${currentToken}`,
      );
      // A request that left before the token was stored, or with a token that
      // has already been replaced, must not clear the session and reload login.
      if (sentTheCurrentToken) {
        localStorage.removeItem('token');
        apiErrorHandlers?.onUnauthorized();
      }
    } else if (status === 403 && method !== 'get') {
      apiErrorHandlers?.onForbidden(problemDetail(error));
    }

    return Promise.reject(error);
  },
);

export const isRedirectingToLogin = () => redirectingToLogin;

export const markRedirectingToLogin = () => {
  redirectingToLogin = true;
};

export const customInstance = <T>(
  url: string,
  config: any
): Promise<T> => {
  const { body, ...rest } = config;

  const axiosConfig: AxiosRequestConfig = {
    url,
    ...rest,
    data: body,
    headers: rest.headers ? Object.fromEntries(new Headers(rest.headers).entries()) : {}
  };

  return api(axiosConfig).then((response: AxiosResponse<T>) => response.data);
};

export default api;
