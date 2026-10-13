import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { UserSummary } from '../../api/types';
import { errorBody, makePost, makeProfile, makeSummary } from '../../test/fixtures';
import { accountHandlers, deferred, renderApp } from '../../test/render';
import { server } from '../../test/server';

const noContent = () => new HttpResponse(null, { status: 204 });

function directory(): UserSummary[] {
  return [
    makeSummary({ id: 2, username: 'bob', followedByMe: false }),
    makeSummary({ id: 3, username: 'carol', followedByMe: true }),
  ];
}

describe('people page', () => {
  it('lists the other accounts with their follow state', async () => {
    const searches: string[] = [];
    server.use(
      http.get('/api/users', ({ request }) => {
        searches.push(new URL(request.url).search);
        return HttpResponse.json(directory());
      }),
    );
    renderApp('/people');

    const list = await screen.findByRole('list', { name: 'People' });
    expect(within(list).getAllByRole('listitem')).toHaveLength(2);
    expect(within(list).getByRole('link', { name: 'bob' })).toHaveAttribute('href', '/u/bob');
    expect(screen.getByRole('button', { name: 'Follow bob' })).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByRole('button', { name: 'Unfollow carol' })).toHaveAttribute('aria-pressed', 'true');
    expect(searches).toEqual(['?limit=50']);
  });

  it('searches by the q parameter of the address and of the search box', async () => {
    const queries: (string | null)[] = [];
    server.use(
      http.get('/api/users', ({ request }) => {
        const q = new URL(request.url).searchParams.get('q');
        queries.push(q);
        return HttpResponse.json(q === 'zz' ? [] : directory().filter((user) => user.username.includes(q ?? '')));
      }),
    );
    const { user, router } = renderApp('/people?q=car');

    expect(await screen.findByRole('button', { name: 'Unfollow carol' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Follow bob' })).not.toBeInTheDocument();
    expect(screen.getByLabelText('Search people by username')).toHaveValue('car');

    await user.clear(screen.getByLabelText('Search people by username'));
    await user.type(screen.getByLabelText('Search people by username'), 'zz');

    expect(await screen.findByText('Nobody matches "zz"')).toBeInTheDocument();
    expect(router.state.location.search).toBe('?q=zz');
    expect(queries[0]).toBe('car');
    expect(queries.at(-1)).toBe('zz');
  });

  it('opens the people page with the query typed in the header search', async () => {
    server.use(
      http.get('/api/posts', () => HttpResponse.json([])),
      http.get('/api/users', () => HttpResponse.json(directory())),
    );
    const { user, router } = renderApp('/');

    await user.type(await screen.findByLabelText('Search people'), 'bob{Enter}');

    expect(await screen.findByRole('heading', { name: 'People' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/people');
    expect(router.state.location.search).toBe('?q=bob');
  });
});

describe('follow toggle', () => {
  it('follows with PUT, flipping the button before the server answers', async () => {
    const answer = deferred();
    const users = directory();
    let followedPath = '';
    server.use(
      http.get('/api/users', () => HttpResponse.json(users)),
      http.put('/api/users/me/following/:username', async ({ request, params }) => {
        followedPath = new URL(request.url).pathname;
        await answer.promise;
        for (const user of users) if (user.username === params.username) user.followedByMe = true;
        return noContent();
      }),
    );
    const { user } = renderApp('/people');

    await user.click(await screen.findByRole('button', { name: 'Follow bob' }));

    // Optimistic state while the request is pending.
    const pending = screen.getByRole('button', { name: 'Unfollow bob' });
    expect(pending).toHaveAttribute('aria-pressed', 'true');
    expect(screen.queryByRole('button', { name: 'Follow bob' })).not.toBeInTheDocument();

    answer.resolve();

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Unfollow bob' })).toBeEnabled();
    });
    expect(followedPath).toBe('/api/users/me/following/bob');
  });

  it('unfollows with DELETE', async () => {
    const users = directory();
    const calls: string[] = [];
    server.use(
      http.get('/api/users', () => HttpResponse.json(users)),
      http.delete('/api/users/me/following/:username', ({ request, params }) => {
        calls.push(`${request.method} ${new URL(request.url).pathname}`);
        for (const user of users) if (user.username === params.username) user.followedByMe = false;
        return noContent();
      }),
    );
    const { user } = renderApp('/people');

    await user.click(await screen.findByRole('button', { name: 'Unfollow carol' }));

    expect(await screen.findByRole('button', { name: 'Follow carol' })).toHaveAttribute('aria-pressed', 'false');
    expect(calls).toEqual(['DELETE /api/users/me/following/carol']);
  });

  it('restores the button and reports the error when the server refuses', async () => {
    const answer = deferred();
    server.use(
      http.get('/api/users', () => HttpResponse.json(directory())),
      http.put('/api/users/me/following/bob', async () => {
        await answer.promise;
        return HttpResponse.json(errorBody('not_found', 'This account no longer exists'), { status: 404 });
      }),
    );
    const { user } = renderApp('/people');

    await user.click(await screen.findByRole('button', { name: 'Follow bob' }));
    expect(screen.getByRole('button', { name: 'Unfollow bob' })).toBeInTheDocument();

    answer.resolve();

    expect(await screen.findByRole('alert')).toHaveTextContent('This account no longer exists');
    expect(screen.getByRole('button', { name: 'Follow bob' })).toHaveAttribute('aria-pressed', 'false');
    expect(screen.queryByRole('button', { name: 'Unfollow bob' })).not.toBeInTheDocument();
  });
});

describe('following panel', () => {
  it('lists the accounts the user follows', async () => {
    server.use(
      ...accountHandlers([makeSummary({ id: 3, username: 'carol', followedByMe: true })]),
      http.get('/api/posts', () => HttpResponse.json([])),
    );
    renderApp('/', { account: false });

    const panel = await screen.findByRole('complementary', { name: 'Following' });
    expect(await within(panel).findByRole('link', { name: 'carol' })).toHaveAttribute('href', '/u/carol');
    expect(within(panel).getByRole('link', { name: 'Messages' })).toHaveAttribute('href', '/chat');
  });
});

describe('profile page', () => {
  function profileHandlers(followedByMe: boolean) {
    const profile = makeProfile({ followedByMe, followerCount: 7, followingCount: 4 });
    const feedRequests: string[] = [];
    server.use(
      http.get('/api/users/bob', () => HttpResponse.json(profile)),
      http.get('/api/users/bob/following', () =>
        HttpResponse.json([makeSummary({ id: 3, username: 'carol', followedByMe: false })]),
      ),
      http.get('/api/posts', ({ request }) => {
        feedRequests.push(new URL(request.url).search);
        return HttpResponse.json([makePost({ content: 'Bob was here' })]);
      }),
    );
    return { profile, feedRequests };
  }

  it("shows the counts and the user's posts", async () => {
    const { feedRequests } = profileHandlers(false);
    renderApp('/u/bob');

    expect(await screen.findByRole('heading', { name: 'bob', level: 1 })).toBeInTheDocument();
    const summary = screen.getByRole('region', { name: 'Profile summary' });
    expect(within(summary).getByText('Following').nextElementSibling).toHaveTextContent('4');
    expect(within(summary).getByText('Followers').nextElementSibling).toHaveTextContent('7');

    const posts = await screen.findByRole('list', { name: 'Posts by bob' });
    expect(within(posts).getByRole('article')).toHaveTextContent('Bob was here');
    expect(feedRequests).toEqual(['?feed=user&author=bob&limit=20']);
    // Someone else's profile: no composer, no private details.
    expect(screen.queryByRole('button', { name: "What's on your mind?" })).not.toBeInTheDocument();
    expect(screen.queryByText('alice@example.org')).not.toBeInTheDocument();
  });

  it('follows from the profile and raises the follower count at once', async () => {
    const answer = deferred();
    const { profile } = profileHandlers(false);
    server.use(
      http.put('/api/users/me/following/bob', async () => {
        await answer.promise;
        profile.followedByMe = true;
        profile.followerCount = 8;
        return noContent();
      }),
    );
    const { user } = renderApp('/u/bob');
    const summary = await screen.findByRole('region', { name: 'Profile summary' });

    await user.click(within(summary).getByRole('button', { name: 'Follow bob' }));

    expect(within(summary).getByRole('button', { name: 'Unfollow bob' })).toBeInTheDocument();
    expect(within(summary).getByText('Followers').nextElementSibling).toHaveTextContent('8');

    answer.resolve();
    await waitFor(() => {
      expect(within(summary).getByRole('button', { name: 'Unfollow bob' })).toBeEnabled();
    });
  });

  it('shows the own profile with the composer and private details, without a follow button', async () => {
    server.use(
      http.get('/api/users/alice', () =>
        HttpResponse.json(makeProfile({ id: 1, username: 'alice', followerCount: 1, followedByMe: false })),
      ),
      http.get('/api/posts', () => HttpResponse.json([])),
    );
    renderApp('/u/alice');

    expect(await screen.findByRole('heading', { name: 'alice', level: 1 })).toBeInTheDocument();
    expect(screen.getByText('Follower').nextElementSibling).toHaveTextContent('1');
    expect(screen.getByText('alice@example.org')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: "What's on your mind?" })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /follow alice/i })).not.toBeInTheDocument();
    expect(await screen.findByText('You have not posted yet')).toBeInTheDocument();
  });

  it('says so when the account does not exist', async () => {
    server.use(
      http.get('/api/users/ghost', () => HttpResponse.json(errorBody('not_found', 'No such user'), { status: 404 })),
    );
    renderApp('/u/ghost');

    expect(await screen.findByText('@ghost does not exist')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Browse people' })).toHaveAttribute('href', '/people');
  });
});
