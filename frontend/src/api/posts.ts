import { apiRequest } from './client';
import type { Comment, Post } from './types';

export type FeedSelector = { feed: 'home' } | { feed: 'user'; author: string };

export const FEED_PAGE_SIZE = 20;

/** One page of a feed, newest first; `before` is the id of the last post already loaded. */
export function listPosts(selector: FeedSelector, before: string | undefined, signal?: AbortSignal): Promise<Post[]> {
  return apiRequest('/api/posts', {
    query: {
      feed: selector.feed,
      author: selector.feed === 'user' ? selector.author : undefined,
      before,
      limit: FEED_PAGE_SIZE,
    },
    signal,
  });
}

export function createPost(content: string): Promise<Post> {
  return apiRequest('/api/posts', { method: 'POST', body: { content } });
}

export function deletePost(postId: string): Promise<void> {
  return apiRequest(`/api/posts/${encodeURIComponent(postId)}`, { method: 'DELETE' });
}

export function likePost(postId: string): Promise<void> {
  return apiRequest(`/api/posts/${encodeURIComponent(postId)}/like`, { method: 'PUT' });
}

export function unlikePost(postId: string): Promise<void> {
  return apiRequest(`/api/posts/${encodeURIComponent(postId)}/like`, { method: 'DELETE' });
}

export function listComments(postId: string, signal?: AbortSignal): Promise<Comment[]> {
  return apiRequest(`/api/posts/${encodeURIComponent(postId)}/comments`, { signal });
}

export function createComment(postId: string, content: string): Promise<Comment> {
  return apiRequest(`/api/posts/${encodeURIComponent(postId)}/comments`, { method: 'POST', body: { content } });
}

export function deleteComment(postId: string, commentId: string): Promise<void> {
  return apiRequest(`/api/posts/${encodeURIComponent(postId)}/comments/${encodeURIComponent(commentId)}`, {
    method: 'DELETE',
  });
}
