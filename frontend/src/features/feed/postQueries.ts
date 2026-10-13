import {
  useInfiniteQuery,
  useMutation,
  useQueryClient,
  type InfiniteData,
  type QueryClient,
  type QueryKey,
} from '@tanstack/react-query';
import {
  createPost,
  deletePost,
  FEED_PAGE_SIZE,
  likePost,
  listPosts,
  unlikePost,
  type FeedSelector,
} from '../../api/posts';
import type { Post } from '../../api/types';

type FeedData = InfiniteData<Post[], string | undefined>;
type FeedSnapshot = [QueryKey, FeedData | undefined][];

export const postKeys = {
  all: ['posts'] as const,
  feed: (selector: FeedSelector) =>
    selector.feed === 'home' ? (['posts', 'home'] as const) : (['posts', 'user', selector.author] as const),
};

/** Pages backwards through a feed: the next page starts before the last post already loaded. */
export function useFeed(selector: FeedSelector) {
  return useInfiniteQuery({
    queryKey: postKeys.feed(selector),
    queryFn: ({ pageParam, signal }) => listPosts(selector, pageParam, signal),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => (lastPage.length < FEED_PAGE_SIZE ? undefined : lastPage.at(-1)?.id),
  });
}

/** Applies `update` to one post in every cached feed; returning null removes the post. */
export function updateCachedPost(queryClient: QueryClient, postId: string, update: (post: Post) => Post | null): void {
  queryClient.setQueriesData<FeedData>({ queryKey: postKeys.all }, (data) => {
    if (data === undefined) return data;
    return {
      ...data,
      pages: data.pages.map((page) =>
        page.flatMap((post) => {
          if (post.id !== postId) return [post];
          const next = update(post);
          return next === null ? [] : [next];
        }),
      ),
    };
  });
}

function restoreFeeds(queryClient: QueryClient, snapshot: FeedSnapshot): void {
  for (const [queryKey, data] of snapshot) queryClient.setQueryData(queryKey, data);
}

export function useCreatePost() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (content: string) => createPost(content),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: postKeys.all }),
  });
}

export function useDeletePost() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (postId: string) => deletePost(postId),
    onSuccess: (_result, postId) => {
      // Drop the card at once, then reload: a shortened page would otherwise look like the last one.
      updateCachedPost(queryClient, postId, () => null);
      return queryClient.invalidateQueries({ queryKey: postKeys.all });
    },
  });
}

/** Likes or unlikes a post. The card updates at once and goes back to its previous state if the server refuses. */
export function useToggleLike() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (post: Post) => (post.likedByMe ? unlikePost(post.id) : likePost(post.id)),
    onMutate: async (post) => {
      await queryClient.cancelQueries({ queryKey: postKeys.all });
      const snapshot: FeedSnapshot = queryClient.getQueriesData<FeedData>({ queryKey: postKeys.all });
      updateCachedPost(queryClient, post.id, (cached) => ({
        ...cached,
        likedByMe: !post.likedByMe,
        likeCount: Math.max(0, cached.likeCount + (post.likedByMe ? -1 : 1)),
      }));
      return { snapshot };
    },
    onError: (_error, _post, context) => {
      if (context) restoreFeeds(queryClient, context.snapshot);
    },
  });
}
