import { useState } from 'react';
import { useQuery, useInfiniteQuery } from 'react-query';
import InfiniteScroll from 'react-infinite-scroll-component';
import PostCard from '../components/PostCard';
import UserCard from '../components/UserCard';
import LoadingSpinner from '../components/LoadingSpinner';
import { feedService } from '../services/feedService';
import { userService } from '../services/userService';
import { Post } from '../types';

const Home: React.FC = () => {
  const [posts, setPosts] = useState<Post[]>([]);

  // Fetch feed with infinite scroll
  const {
    data: _feedData,
    fetchNextPage,
    hasNextPage,
    isLoading: isFeedLoading,
  } = useInfiniteQuery(
    'feed',
    ({ pageParam = 0 }) => feedService.getFeed(pageParam, 10),
    {
      getNextPageParam: (lastPage) => (lastPage.hasNext ? lastPage.page + 1 : undefined),
      onSuccess: (data) => {
        const allPosts = data.pages.flatMap((page) => page.content);
        setPosts(allPosts);
      },
    }
  );

  // Fetch suggested users
  const { data: suggestedUsers } = useQuery('suggestedUsers', () =>
    userService.getSuggestedUsers(5)
  );

  const handleLikeToggle = (postId: string, isLiked: boolean) => {
    setPosts((prev) =>
      prev.map((post) =>
        post.id === postId
          ? {
              ...post,
              isLiked,
              likesCount: isLiked ? post.likesCount + 1 : post.likesCount - 1,
            }
          : post
      )
    );
  };

  if (isFeedLoading) {
    return (
      <div className="flex justify-center items-center h-64">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  return (
    <div className="mt-16 flex gap-8">
      {/* Main Feed */}
      <div className="flex-1 max-w-lg">
        {posts.length === 0 ? (
          <div className="bg-white border border-gray-200 rounded-lg p-8 text-center">
            <h3 className="text-xl font-semibold mb-2">Welcome to Instagram</h3>
            <p className="text-gray-500 mb-4">
              Follow people to see their photos and videos in your feed.
            </p>
          </div>
        ) : (
          <InfiniteScroll
            dataLength={posts.length}
            next={fetchNextPage}
            hasMore={hasNextPage ?? false}
            loader={<LoadingSpinner className="py-4" />}
            endMessage={
              <p className="text-center text-gray-500 py-4">
                You're all caught up!
              </p>
            }
          >
            {posts.map((post) => (
              <PostCard
                key={post.id}
                post={post}
                onLikeToggle={handleLikeToggle}
              />
            ))}
          </InfiniteScroll>
        )}
      </div>

      {/* Sidebar - Suggestions */}
      <aside className="hidden lg:block w-80">
        <div className="sticky top-20">
          {suggestedUsers && suggestedUsers.length > 0 && (
            <div className="bg-white border border-gray-200 rounded-lg p-4">
              <div className="flex justify-between items-center mb-4">
                <h3 className="text-gray-500 font-semibold text-sm">
                  Suggestions For You
                </h3>
                <button className="text-xs font-semibold hover:text-gray-500">
                  See All
                </button>
              </div>
              <div className="space-y-2">
                {suggestedUsers.map((user) => (
                  <UserCard
                    key={user.id}
                    user={user}
                    showFollowButton
                    onFollow={(userId) => {
                      userService.followUser(userId);
                    }}
                    onUnfollow={(userId) => {
                      userService.unfollowUser(userId);
                    }}
                  />
                ))}
              </div>
            </div>
          )}

          <footer className="mt-6 text-xs text-gray-400">
            <p className="mb-4">
              About · Help · Press · API · Jobs · Privacy · Terms · Locations · Language
            </p>
            <p>© 2024 INSTAGRAM CLONE</p>
          </footer>
        </div>
      </aside>
    </div>
  );
};

export default Home;
