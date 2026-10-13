import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { Comment, Post } from '../../api/types';
import { errorBody, makeComment, makePage, makePost } from '../../test/fixtures';
import { deferred, renderApp } from '../../test/render';
import { server } from '../../test/server';

function feedOf(posts: Post[]) {
  return http.get('/api/posts', () => HttpResponse.json(posts));
}

const noContent = () => new HttpResponse(null, { status: 204 });

describe('home timeline', () => {
  it('renders the posts of the home feed, newest first', async () => {
    const requests: string[] = [];
    server.use(
      http.get('/api/posts', ({ request }) => {
        requests.push(new URL(request.url).search);
        return HttpResponse.json([
          makePost({ id: 'post-2', content: 'Second post', author: { id: 3, username: 'carol' } }),
          makePost({ id: 'post-1', content: 'First post', likeCount: 3, commentCount: 2 }),
        ]);
      }),
    );
    renderApp('/');

    const timeline = await screen.findByRole('list', { name: 'Home timeline' });
    const posts = within(timeline).getAllByRole('article');
    expect(posts).toHaveLength(2);
    expect(posts[0]).toHaveAccessibleName('Post by carol');
    expect(posts[0]).toHaveTextContent('Second post');
    expect(posts[1]).toHaveTextContent('First post');
    const bobPost = within(timeline).getByRole('article', { name: 'Post by bob' });
    expect(within(bobPost).getByRole('link', { name: 'bob' })).toHaveAttribute('href', '/u/bob');
    expect(within(bobPost).getByRole('button', { name: /^Like/ })).toHaveTextContent('3');
    expect(within(bobPost).getByRole('button', { name: /^Comments/ })).toHaveTextContent('2');
    expect(requests).toEqual(['?feed=home&limit=20']);
    // A short page is the last one.
    expect(screen.queryByRole('button', { name: 'Load older posts' })).not.toBeInTheDocument();
  });

  it('pages backwards with `before` set to the last loaded post', async () => {
    const befores: (string | null)[] = [];
    server.use(
      http.get('/api/posts', ({ request }) => {
        const before = new URL(request.url).searchParams.get('before');
        befores.push(before);
        return HttpResponse.json(before === null ? makePage(40, 20) : makePage(20, 5));
      }),
    );
    const { user } = renderApp('/');

    const timeline = await screen.findByRole('list', { name: 'Home timeline' });
    expect(within(timeline).getAllByRole('article')).toHaveLength(20);

    await user.click(screen.getByRole('button', { name: 'Load older posts' }));

    await waitFor(() => {
      expect(within(timeline).getAllByRole('article')).toHaveLength(25);
    });
    expect(befores).toEqual([null, 'post-21']);
    expect(within(timeline).getAllByRole('article').at(-1)).toHaveTextContent('Post number 16');
    expect(screen.queryByRole('button', { name: 'Load older posts' })).not.toBeInTheDocument();
  });

  it('shows an empty state when there is nothing to read', async () => {
    server.use(feedOf([]));
    renderApp('/');

    expect(await screen.findByText('Your timeline is empty')).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: 'Home timeline' })).not.toBeInTheDocument();
  });

  it('shows an error with a retry that loads the feed', async () => {
    let attempts = 0;
    server.use(
      http.get('/api/posts', () => {
        attempts += 1;
        return attempts === 1
          ? HttpResponse.json(errorBody('internal_error', 'The feed is unavailable'), { status: 500 })
          : HttpResponse.json([makePost()]);
      }),
    );
    const { user } = renderApp('/');

    expect(await screen.findByRole('alert')).toHaveTextContent('The feed is unavailable');

    await user.click(screen.getByRole('button', { name: 'Try again' }));

    expect(await screen.findByRole('article', { name: 'Post by bob' })).toBeInTheDocument();
  });
});

describe('like', () => {
  it('updates the card before the server answers and keeps the new state on success', async () => {
    const answer = deferred();
    let method = '';
    server.use(
      feedOf([makePost({ likeCount: 3, likedByMe: false })]),
      http.put('/api/posts/post-1/like', async ({ request }) => {
        method = request.method;
        await answer.promise;
        return noContent();
      }),
    );
    const { user } = renderApp('/');
    const like = await screen.findByRole('button', { name: /^Like/ });
    expect(like).toHaveAttribute('aria-pressed', 'false');

    await user.click(like);

    // The request is still pending: this is the optimistic state.
    expect(like).toHaveAttribute('aria-pressed', 'true');
    expect(like).toHaveTextContent('4');

    answer.resolve();
    await waitFor(() => {
      expect(like).toBeEnabled();
    });
    expect(method).toBe('PUT');
    expect(like).toHaveAttribute('aria-pressed', 'true');
    expect(like).toHaveTextContent('4');
  });

  it('rolls the card back and reports the error when the server refuses', async () => {
    const answer = deferred();
    server.use(
      feedOf([makePost({ likeCount: 3, likedByMe: false })]),
      http.put('/api/posts/post-1/like', async () => {
        await answer.promise;
        return HttpResponse.json(errorBody('internal_error', 'Could not save the like'), { status: 500 });
      }),
    );
    const { user } = renderApp('/');
    const like = await screen.findByRole('button', { name: /^Like/ });

    await user.click(like);
    expect(like).toHaveTextContent('4');

    answer.resolve();

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not save the like');
    expect(like).toHaveAttribute('aria-pressed', 'false');
    expect(like).toHaveTextContent('3');
  });

  it('removes the like of an already liked post with DELETE', async () => {
    let method = '';
    server.use(
      feedOf([makePost({ likeCount: 1, likedByMe: true })]),
      http.delete('/api/posts/post-1/like', ({ request }) => {
        method = request.method;
        return noContent();
      }),
    );
    const { user } = renderApp('/');
    const like = await screen.findByRole('button', { name: /^Like/ });
    expect(like).toHaveAttribute('aria-pressed', 'true');

    await user.click(like);

    await waitFor(() => {
      expect(method).toBe('DELETE');
    });
    expect(like).toHaveAttribute('aria-pressed', 'false');
    expect(like).toHaveTextContent('0');
  });
});

describe('own posts', () => {
  it('offers to delete only the posts of the signed-in user', async () => {
    server.use(
      feedOf([
        makePost({ id: 'mine', author: { id: 1, username: 'alice' }, ownedByMe: true }),
        makePost({ id: 'theirs', ownedByMe: false }),
      ]),
    );
    renderApp('/');

    const mine = await screen.findByRole('article', { name: 'Post by alice' });
    const theirs = screen.getByRole('article', { name: 'Post by bob' });
    expect(within(mine).getByRole('button', { name: 'Delete post' })).toBeInTheDocument();
    expect(within(theirs).queryByRole('button', { name: 'Delete post' })).not.toBeInTheDocument();
  });

  it('deletes a post and removes its card before the feed is reloaded', async () => {
    const reload = deferred();
    let posts = [makePost({ id: 'mine', author: { id: 1, username: 'alice' }, ownedByMe: true }), makePost()];
    let feedRequests = 0;
    let deletedPath = '';
    server.use(
      http.get('/api/posts', async () => {
        feedRequests += 1;
        if (feedRequests > 1) await reload.promise;
        return HttpResponse.json(posts);
      }),
      http.delete('/api/posts/:id', ({ request, params }) => {
        deletedPath = new URL(request.url).pathname;
        posts = posts.filter((post) => post.id !== params.id);
        return noContent();
      }),
    );
    const { user } = renderApp('/');

    await user.click(await screen.findByRole('button', { name: 'Delete post' }));

    // The reload is still held back: the card is already gone.
    await waitFor(() => {
      expect(screen.queryByRole('article', { name: 'Post by alice' })).not.toBeInTheDocument();
    });
    expect(deletedPath).toBe('/api/posts/mine');

    reload.resolve();
    await waitFor(() => {
      expect(feedRequests).toBe(2);
    });
    expect(screen.getByRole('article', { name: 'Post by bob' })).toBeInTheDocument();
    expect(screen.queryByRole('article', { name: 'Post by alice' })).not.toBeInTheDocument();
  });

  it('still offers older posts after deleting one from a full page', async () => {
    let posts = [makePost({ id: 'mine', author: { id: 1, username: 'alice' }, ownedByMe: true }), ...makePage(40, 25)];
    server.use(
      http.get('/api/posts', ({ request }) => {
        const before = new URL(request.url).searchParams.get('before');
        const start = before === null ? 0 : posts.findIndex((post) => post.id === before) + 1;
        return HttpResponse.json(posts.slice(start, start + 20));
      }),
      http.delete('/api/posts/:id', ({ params }) => {
        posts = posts.filter((post) => post.id !== params.id);
        return noContent();
      }),
    );
    const { user } = renderApp('/');
    const timeline = await screen.findByRole('list', { name: 'Home timeline' });

    await user.click(within(timeline).getByRole('button', { name: 'Delete post' }));

    await waitFor(() => {
      expect(within(timeline).getAllByRole('article')[0]).toHaveTextContent('Post number 40');
    });
    await waitFor(() => {
      expect(within(timeline).getAllByRole('article')).toHaveLength(20);
    });
    expect(screen.getByRole('button', { name: 'Load older posts' })).toBeInTheDocument();
  });

  it('publishes a post from the composer and reloads the feed', async () => {
    const posts: Post[] = [];
    let body: unknown = null;
    server.use(
      http.get('/api/posts', () => HttpResponse.json(posts)),
      http.post('/api/posts', async ({ request }) => {
        body = await request.json();
        const created = makePost({ id: 'new', content: 'Hello world', author: { id: 1, username: 'alice' } });
        posts.unshift(created);
        return HttpResponse.json(created, { status: 201 });
      }),
    );
    const { user } = renderApp('/');
    await screen.findByText('Your timeline is empty');

    await user.click(screen.getByRole('button', { name: "What's on your mind?" }));
    const dialog = screen.getByRole('dialog', { name: 'Create a post' });
    const publish = within(dialog).getByRole('button', { name: 'Publish' });
    expect(publish).toBeDisabled();
    expect(within(dialog).getByLabelText('Post content')).toHaveFocus();

    await user.type(within(dialog).getByLabelText('Post content'), '  Hello world  ');
    await user.click(publish);

    expect(await screen.findByRole('article', { name: 'Post by alice' })).toHaveTextContent('Hello world');
    expect(body).toEqual({ content: 'Hello world' });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('keeps the composer open and shows the field error of the server', async () => {
    server.use(
      feedOf([]),
      http.post('/api/posts', () =>
        HttpResponse.json(
          errorBody('validation_failed', 'Invalid request', { content: 'size must be between 1 and 500' }),
          { status: 400 },
        ),
      ),
    );
    const { user } = renderApp('/');
    await screen.findByText('Your timeline is empty');

    await user.click(screen.getByRole('button', { name: "What's on your mind?" }));
    await user.type(screen.getByLabelText('Post content'), 'Hello');
    await user.click(screen.getByRole('button', { name: 'Publish' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('size must be between 1 and 500');
    expect(screen.getByLabelText('Post content')).toBeInvalid();
    expect(screen.getByLabelText('Post content')).toHaveValue('Hello');

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: "What's on your mind?" })).toHaveFocus();
  });
});

describe('comments', () => {
  function commentsOf(comments: Comment[]) {
    return http.get('/api/posts/post-1/comments', () => HttpResponse.json(comments));
  }

  const bobComment = makeComment({ id: 'c1', content: 'From Bob', ownedByMe: false });
  const aliceComment = makeComment({
    id: 'c2',
    content: 'From Alice',
    author: { id: 1, username: 'alice' },
    ownedByMe: true,
  });

  it('lists the comments once opened, oldest first', async () => {
    server.use(feedOf([makePost({ commentCount: 2 })]), commentsOf([bobComment, aliceComment]));
    const { user } = renderApp('/');

    const toggle = await screen.findByRole('button', { name: /^Comments/ });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await user.click(toggle);

    const thread = await screen.findByRole('region', { name: 'Comments on the post by bob' });
    const items = await within(thread).findAllByRole('listitem');
    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent('From Bob');
    expect(items[1]).toHaveTextContent('From Alice');
  });

  it("shows the delete button only on the user's own comments under someone else's post", async () => {
    server.use(feedOf([makePost({ ownedByMe: false })]), commentsOf([bobComment, aliceComment]));
    const { user } = renderApp('/');

    await user.click(await screen.findByRole('button', { name: /^Comments/ }));

    expect(await screen.findByRole('button', { name: 'Delete comment by alice' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Delete comment by bob' })).not.toBeInTheDocument();
  });

  it('lets the author of the post delete any comment under it', async () => {
    server.use(
      feedOf([makePost({ author: { id: 1, username: 'alice' }, ownedByMe: true })]),
      commentsOf([bobComment]),
    );
    const { user } = renderApp('/');

    await user.click(await screen.findByRole('button', { name: /^Comments/ }));

    expect(await screen.findByRole('button', { name: 'Delete comment by bob' })).toBeInTheDocument();
  });

  it('deletes a comment and lowers the counter', async () => {
    let deletedPath = '';
    server.use(
      feedOf([makePost({ commentCount: 2 })]),
      commentsOf([bobComment, aliceComment]),
      http.delete('/api/posts/:postId/comments/:commentId', ({ request }) => {
        deletedPath = new URL(request.url).pathname;
        return noContent();
      }),
    );
    const { user } = renderApp('/');
    const toggle = await screen.findByRole('button', { name: /^Comments/ });
    await user.click(toggle);

    await user.click(await screen.findByRole('button', { name: 'Delete comment by alice' }));

    await waitFor(() => {
      expect(screen.queryByText('From Alice')).not.toBeInTheDocument();
    });
    expect(deletedPath).toBe('/api/posts/post-1/comments/c2');
    expect(screen.getByText('From Bob')).toBeInTheDocument();
    expect(toggle).toHaveTextContent('1');
  });

  it('adds a comment, appends it to the thread and raises the counter', async () => {
    let body: unknown = null;
    server.use(
      feedOf([makePost({ commentCount: 0 })]),
      commentsOf([]),
      http.post('/api/posts/post-1/comments', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(makeComment({ ...aliceComment, id: 'c9', content: 'Great post' }), { status: 201 });
      }),
    );
    const { user } = renderApp('/');
    const toggle = await screen.findByRole('button', { name: /^Comments/ });
    await user.click(toggle);
    expect(await screen.findByText('No comments yet.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Send comment' })).toBeDisabled();

    await user.type(screen.getByLabelText('Write a comment'), 'Great post');
    await user.click(screen.getByRole('button', { name: 'Send comment' }));

    const thread = screen.getByRole('region', { name: 'Comments on the post by bob' });
    expect(await within(thread).findByText('Great post')).toBeInTheDocument();
    expect(body).toEqual({ content: 'Great post' });
    expect(screen.getByLabelText('Write a comment')).toHaveValue('');
    expect(toggle).toHaveTextContent('1');
    expect(screen.getByRole('button', { name: 'Delete comment by alice' })).toBeInTheDocument();
  });

  it('shows why a comment was refused and keeps the text', async () => {
    server.use(
      feedOf([makePost()]),
      commentsOf([]),
      http.post('/api/posts/post-1/comments', () =>
        HttpResponse.json(errorBody('validation_failed', 'Invalid request', { content: 'must not be blank' }), {
          status: 400,
        }),
      ),
    );
    const { user } = renderApp('/');
    await user.click(await screen.findByRole('button', { name: /^Comments/ }));
    await screen.findByText('No comments yet.');

    await user.type(screen.getByLabelText('Write a comment'), 'x');
    await user.click(screen.getByRole('button', { name: 'Send comment' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('must not be blank');
    expect(screen.getByLabelText('Write a comment')).toHaveValue('x');
  });
});
