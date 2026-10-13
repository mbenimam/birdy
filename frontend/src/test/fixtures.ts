import type { ChatMessage, Comment, CurrentUser, Post, Session, UserProfile, UserSummary } from '../api/types';

export const TOKEN = 'test-token';

export const alice: CurrentUser = {
  id: 1,
  username: 'alice',
  email: 'alice@example.org',
  birthday: '1990-04-12',
  createdAt: '2026-01-01T10:00:00Z',
  followingCount: 1,
  followerCount: 2,
};

export const aliceSession: Session = {
  token: TOKEN,
  expiresAt: '2026-12-31T00:00:00Z',
  user: alice,
};

export function makePost(overrides: Partial<Post> = {}): Post {
  return {
    id: 'post-1',
    author: { id: 2, username: 'bob' },
    content: 'Hello from Bob',
    createdAt: '2026-10-01T12:00:00Z',
    likeCount: 3,
    likedByMe: false,
    commentCount: 0,
    ownedByMe: false,
    ...overrides,
  };
}

/** `count` posts with ids `post-<from>` downwards (newest first, like the API). */
export function makePage(from: number, count: number): Post[] {
  return Array.from({ length: count }, (_, index) => {
    const number = from - index;
    return makePost({ id: `post-${number}`, content: `Post number ${number}` });
  });
}

export function makeComment(overrides: Partial<Comment> = {}): Comment {
  return {
    id: 'comment-1',
    author: { id: 2, username: 'bob' },
    content: 'Nice one',
    createdAt: '2026-10-01T12:05:00Z',
    ownedByMe: false,
    ...overrides,
  };
}

export function makeSummary(overrides: Partial<UserSummary> = {}): UserSummary {
  return { id: 2, username: 'bob', followedByMe: false, ...overrides };
}

export function makeProfile(overrides: Partial<UserProfile> = {}): UserProfile {
  return {
    id: 2,
    username: 'bob',
    createdAt: '2026-02-01T10:00:00Z',
    followingCount: 4,
    followerCount: 7,
    followedByMe: false,
    ...overrides,
  };
}

export function makeChatMessage(overrides: Partial<ChatMessage> = {}): ChatMessage {
  return {
    type: 'message',
    id: 'message-1',
    room: 'lobby',
    author: { id: 2, username: 'bob' },
    content: 'Hi lobby',
    sentAt: '2026-10-01T12:00:00Z',
    ...overrides,
  };
}

export function errorBody(error: string, message: string, fields?: Record<string, string>) {
  return fields === undefined ? { error, message } : { error, message, fields };
}
