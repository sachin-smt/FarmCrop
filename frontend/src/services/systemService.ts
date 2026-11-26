import { api } from './apiClient';

export interface ApiStatus {
  service: string;
  apiVersion: string;
  status: 'UP' | string;
}

/** Confirms the backend is reachable. */
export function getApiStatus(signal?: AbortSignal) {
  return api.get<ApiStatus>('/public/status', { auth: false, signal });
}
