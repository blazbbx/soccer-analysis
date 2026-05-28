import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios';

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {    
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

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