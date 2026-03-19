import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
});

// Ez egy globális változó lesz, amit az AuthProvider frissít
let authToken: string | null = null;

export const setAuthTokenForApi = (token: string | null) => {
  authToken = token;
};

api.interceptors.request.use((config) => {
  if (authToken) {
    config.headers.Authorization = `Bearer ${authToken}`;
  }
  return config;
});

export default api;