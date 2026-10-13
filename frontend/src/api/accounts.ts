import { apiRequest } from './client';
import type { Credentials, CurrentUser, Session, SignupRequest, User, UserProfile, UserSummary } from './types';

export function createUser(request: SignupRequest): Promise<User> {
  return apiRequest('/api/users', { method: 'POST', body: request });
}

export function createSession(credentials: Credentials): Promise<Session> {
  return apiRequest('/api/sessions', { method: 'POST', body: credentials });
}

export function deleteCurrentSession(): Promise<void> {
  return apiRequest('/api/sessions/current', { method: 'DELETE' });
}

export function getCurrentUser(signal?: AbortSignal): Promise<CurrentUser> {
  return apiRequest('/api/users/me', { signal });
}

export function searchUsers(q: string, signal?: AbortSignal): Promise<UserSummary[]> {
  return apiRequest('/api/users', {
    query: { q: q === '' ? undefined : q, limit: 50 },
    signal,
  });
}

export function getUserProfile(username: string, signal?: AbortSignal): Promise<UserProfile> {
  return apiRequest(`/api/users/${encodeURIComponent(username)}`, { signal });
}

export function getFollowing(username: string, signal?: AbortSignal): Promise<UserSummary[]> {
  return apiRequest(`/api/users/${encodeURIComponent(username)}/following`, { signal });
}

export function follow(username: string): Promise<void> {
  return apiRequest(`/api/users/me/following/${encodeURIComponent(username)}`, { method: 'PUT' });
}

export function unfollow(username: string): Promise<void> {
  return apiRequest(`/api/users/me/following/${encodeURIComponent(username)}`, { method: 'DELETE' });
}
