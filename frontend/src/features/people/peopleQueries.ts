import { useMutation, useQuery, useQueryClient, type QueryKey } from '@tanstack/react-query';
import { follow, getFollowing, getUserProfile, searchUsers, unfollow } from '../../api/accounts';
import type { UserProfile, UserSummary } from '../../api/types';
import { postKeys } from '../feed/postQueries';

export const peopleKeys = {
  lists: ['users'] as const,
  search: (q: string) => ['users', 'search', q] as const,
  following: (username: string) => ['users', 'following', username] as const,
  profiles: ['profile'] as const,
  profile: (username: string) => ['profile', username] as const,
};

export function useUserSearch(q: string) {
  return useQuery({
    queryKey: peopleKeys.search(q),
    queryFn: ({ signal }) => searchUsers(q, signal),
    placeholderData: (previous) => previous,
  });
}

export function useFollowing(username: string) {
  return useQuery({
    queryKey: peopleKeys.following(username),
    queryFn: ({ signal }) => getFollowing(username, signal),
  });
}

export function useProfile(username: string) {
  return useQuery({
    queryKey: peopleKeys.profile(username),
    queryFn: ({ signal }) => getUserProfile(username, signal),
  });
}

interface FollowToggle {
  username: string;
  /** State before the click. */
  followedByMe: boolean;
}

interface FollowSnapshot {
  lists: [QueryKey, UserSummary[] | undefined][];
  profile: UserProfile | undefined;
}

/**
 * Follows or unfollows an account. Every cached list and the cached profile flip at once,
 * are restored if the server refuses, and are refreshed from the server afterwards.
 */
export function useToggleFollow() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ username, followedByMe }: FollowToggle) => (followedByMe ? unfollow(username) : follow(username)),
    onMutate: async ({ username, followedByMe }) => {
      await Promise.all([
        queryClient.cancelQueries({ queryKey: peopleKeys.lists }),
        queryClient.cancelQueries({ queryKey: peopleKeys.profile(username) }),
      ]);
      const snapshot: FollowSnapshot = {
        lists: queryClient.getQueriesData<UserSummary[]>({ queryKey: peopleKeys.lists }),
        profile: queryClient.getQueryData<UserProfile>(peopleKeys.profile(username)),
      };

      queryClient.setQueriesData<UserSummary[]>({ queryKey: peopleKeys.lists }, (users) =>
        users?.map((user) => (user.username === username ? { ...user, followedByMe: !followedByMe } : user)),
      );
      queryClient.setQueryData<UserProfile>(peopleKeys.profile(username), (profile) =>
        profile === undefined
          ? profile
          : {
              ...profile,
              followedByMe: !followedByMe,
              followerCount: Math.max(0, profile.followerCount + (followedByMe ? -1 : 1)),
            },
      );
      return snapshot;
    },
    onError: (_error, { username }, snapshot) => {
      if (snapshot === undefined) return;
      for (const [queryKey, users] of snapshot.lists) queryClient.setQueryData(queryKey, users);
      queryClient.setQueryData(peopleKeys.profile(username), snapshot.profile);
    },
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: peopleKeys.lists }),
        queryClient.invalidateQueries({ queryKey: peopleKeys.profiles }),
        queryClient.invalidateQueries({ queryKey: postKeys.feed({ feed: 'home' }) }),
      ]),
  });
}
