/** Shapes of docs/api.md. Timestamps are ISO-8601 UTC strings. */

export interface UserRef {
  id: number;
  username: string;
}

export interface User extends UserRef {
  createdAt: string;
  followingCount: number;
  followerCount: number;
}

/** GET /api/users/me: the only place where email and birthday are returned. */
export interface CurrentUser extends User {
  email: string;
  birthday: string;
}

/** GET /api/users/{username}. */
export interface UserProfile extends User {
  followedByMe: boolean;
}

export interface UserSummary extends UserRef {
  followedByMe: boolean;
}

export interface Post {
  id: string;
  author: UserRef;
  content: string;
  createdAt: string;
  likeCount: number;
  likedByMe: boolean;
  commentCount: number;
  ownedByMe: boolean;
}

export interface Comment {
  id: string;
  author: UserRef;
  content: string;
  createdAt: string;
  ownedByMe: boolean;
}

export interface ChatMessage {
  type: 'message';
  id: string;
  room: string;
  author: UserRef;
  content: string;
  sentAt: string;
}

export interface Presence {
  type: 'presence';
  room: string;
  users: string[];
}

export type ChatFrame = ChatMessage | Presence;

export interface Session {
  token: string;
  expiresAt: string;
  user: User;
}

export interface ChatTicket {
  ticket: string;
  expiresAt: string;
}

export interface Credentials {
  username: string;
  password: string;
}

export interface SignupRequest extends Credentials {
  email: string;
  birthday: string;
}
