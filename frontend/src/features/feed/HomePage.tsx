import { Composer } from './Composer';
import { Timeline } from './Timeline';

export function HomePage() {
  return (
    <div className="content">
      <h1 className="sr-only">Timeline</h1>
      <div className="content__body">
        <div className="content__body-main">
          <Composer />
          <Timeline
            selector={{ feed: 'home' }}
            label="Home timeline"
            emptyTitle="Your timeline is empty"
            emptyHint="Write your first post or follow people to see theirs here."
          />
        </div>
      </div>
    </div>
  );
}
