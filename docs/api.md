# Birdy HTTP and WebSocket API

All REST paths are under `/api`. Bodies are JSON (`application/json`, UTF-8). Timestamps are ISO-8601 UTC strings.
Every route except the three marked *public* needs `Authorization: Bearer <token>`.

## Errors

Any failure returns a matching HTTP status and this body:

```json
{ "error": "validation_failed", "message": "Human readable text", "fields": { "email": "must be a well-formed email address" } }
```

`fields` is present only for `400 validation_failed`. Codes: `validation_failed` (400), `unauthorized` (401),
`forbidden` (403), `not_found` (404), `conflict` (409), `internal_error` (500).

## Shapes

```text
User        { id: number, username: string, email: string, birthday: "YYYY-MM-DD", createdAt: string,
              followingCount: number, followerCount: number }          (email and birthday only on /users/me)
UserSummary { id: number, username: string, followedByMe: boolean }
Post        { id: string, author: { id: number, username: string }, content: string, createdAt: string,
              likeCount: number, likedByMe: boolean, commentCount: number, ownedByMe: boolean }
Comment     { id: string, author: { id: number, username: string }, content: string, createdAt: string, ownedByMe: boolean }
ChatMessage { type: "message", id: string, room: string, author: { id: number, username: string }, content: string, sentAt: string }
Presence    { type: "presence", room: string, users: string[] }        (usernames currently connected to the room)
```

## Accounts and sessions

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/api/users` *public* | `{ username, email, password, birthday }` | `201` User | username 3-30 chars `[A-Za-z0-9_]`, password 8-128 chars, `409 conflict` when the username or email is taken |
| POST | `/api/sessions` *public* | `{ username, password }` | `201` `{ token, expiresAt, user: User }` | `401 unauthorized` on a wrong pair, same message for unknown user and wrong password |
| DELETE | `/api/sessions/current` | | `204` | deletes the session of the presented token |
| GET | `/api/users/me` | | `200` User | |
| DELETE | `/api/users/me` | | `204` | deletes the account, its sessions, its follow links, its posts and comments |
| GET | `/api/users?q=<text>&limit=<n>` | | `200` UserSummary[] | `q` optional (substring of the username, case-insensitive), limit default 20, max 50, never includes the caller |
| GET | `/api/users/{username}` | | `200` User (without email and birthday) plus `followedByMe` | `404` |

## Following (the "friends" of the UI)

| Method | Path | Success | Notes |
|---|---|---|---|
| PUT | `/api/users/me/following/{username}` | `204` | idempotent, `404` unknown user, `400` when following oneself |
| DELETE | `/api/users/me/following/{username}` | `204` | idempotent |
| GET | `/api/users/{username}/following` | `200` UserSummary[] | |
| GET | `/api/users/{username}/followers` | `200` UserSummary[] | |

## Posts, likes, comments

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/api/posts` | `{ content }` | `201` Post | content 1-500 chars after trimming |
| GET | `/api/posts?feed=home&before=<postId>&limit=<n>` | | `200` Post[] | newest first; `feed=home` is the caller plus the users they follow; `feed=user&author=<username>` is one user's posts; limit default 20, max 50; `before` pages backwards |
| GET | `/api/posts/{id}` | | `200` Post | `404` |
| DELETE | `/api/posts/{id}` | | `204` | `403` when the caller is not the author |
| PUT | `/api/posts/{id}/like` | | `204` | idempotent |
| DELETE | `/api/posts/{id}/like` | | `204` | idempotent |
| GET | `/api/posts/{id}/comments` | | `200` Comment[] | oldest first |
| POST | `/api/posts/{id}/comments` | `{ content }` | `201` Comment | content 1-300 chars |
| DELETE | `/api/posts/{id}/comments/{commentId}` | | `204` | `403` unless the caller wrote the comment or the post |

## Chat

| Method | Path | Success | Notes |
|---|---|---|---|
| POST | `/api/chat/tickets` | `201` `{ ticket, expiresAt }` | single-use ticket, valid 30 seconds |
| GET | `/api/chat/rooms/{room}/messages?limit=<n>` | `200` ChatMessage[] | the last `limit` messages (default 50, max 100), oldest first |

WebSocket: `GET /ws/chat/{room}?ticket=<ticket>`. `room` matches `[a-z0-9-]{1,32}`. The server closes with code `1008` when the
ticket is missing, unknown, expired or already used. The client sends `{ "content": "text" }` (1-1000 chars). The server stores
each message and sends a `ChatMessage` to every connection in the room, the sender included. On every join and leave the
server sends a `Presence` frame to the room.

## Health

`GET /api/health` *public* returns `200 { "status": "UP", "mysql": "UP", "mongo": "UP" }`, or `503` with the failing store marked `DOWN`.

## Storage

MySQL holds `users`, `user_sessions` and `follows`. MongoDB holds `posts` (comments embedded, likes as an array of user ids)
and `messages`. Passwords are stored as PBKDF2-HMAC-SHA256 with a per-user random salt. Session tokens are 32 random bytes
from `SecureRandom`; only their SHA-256 digest is stored, with an expiry.

## Details the tables leave out

- Usernames and emails are compared without regard to case. The username `me` is reserved.
- Malformed JSON, an unparsable date, and invalid `limit`, `before`, `feed` or room values return `400 validation_failed`.
- Other client errors without a code of their own (405, 415) keep their HTTP status and use `validation_failed` in the body.
- WebSocket frames that are not JSON, are blank, or exceed 1000 characters are dropped without a reply.
- Deleting an account keeps the chat messages it sent; its posts, comments and likes are removed.
- Chat tickets and room membership are held in memory by one server instance.
