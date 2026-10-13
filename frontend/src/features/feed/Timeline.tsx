import { errorMessage } from '../../api/errors';
import type { FeedSelector } from '../../api/posts';
import { EmptyState, ErrorState, LoadingState } from '../../components/States';
import { PostCard } from './PostCard';
import { useFeed } from './postQueries';

interface TimelineProps {
  selector: FeedSelector;
  /** Accessible name of the list. */
  label: string;
  emptyTitle: string;
  emptyHint?: string;
}

/** A feed of post cards, newest first, with a button loading the older ones. */
export function Timeline({ selector, label, emptyTitle, emptyHint }: TimelineProps) {
  const feed = useFeed(selector);

  if (feed.isPending) return <LoadingState label="Loading posts" />;
  if (feed.isError && feed.data === undefined) {
    return (
      <ErrorState
        title="The posts could not be loaded"
        message={errorMessage(feed.error)}
        onRetry={() => void feed.refetch()}
      />
    );
  }

  const posts = feed.data.pages.flat();
  if (posts.length === 0) {
    return <EmptyState title={emptyTitle}>{emptyHint !== undefined && <p>{emptyHint}</p>}</EmptyState>;
  }

  return (
    <>
      <ul className="card-container card-container--timeline" aria-label={label}>
        {posts.map((post) => (
          <PostCard key={post.id} post={post} />
        ))}
      </ul>
      {feed.isFetchNextPageError && (
        <p className="inline-error" role="alert">
          {errorMessage(feed.error)}
        </p>
      )}
      {feed.hasNextPage && (
        <button
          type="button"
          className="btn-text timeline__more"
          disabled={feed.isFetchingNextPage}
          onClick={() => void feed.fetchNextPage()}
        >
          {feed.isFetchingNextPage ? 'Loading…' : 'Load older posts'}
        </button>
      )}
    </>
  );
}
