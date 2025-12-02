import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from 'react-query';
import { MagnifyingGlassIcon } from '@heroicons/react/24/outline';
import { userService } from '../services/userService';
import { postService } from '../services/postService';
import UserCard from '../components/UserCard';
import LoadingSpinner from '../components/LoadingSpinner';
import { Link } from 'react-router-dom';

type SearchTab = 'users' | 'posts' | 'hashtags';

const Search: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const initialQuery = searchParams.get('q') || '';
  const initialHashtag = searchParams.get('hashtag') || '';
  
  const [query, setQuery] = useState(initialQuery || initialHashtag);
  const [activeTab, setActiveTab] = useState<SearchTab>(initialHashtag ? 'hashtags' : 'users');
  const [searchTerm, setSearchTerm] = useState(initialQuery || initialHashtag);

  // Search users
  const { data: usersData, isLoading: isUsersLoading } = useQuery(
    ['searchUsers', searchTerm],
    () => userService.searchUsers(searchTerm, 0, 20),
    { enabled: activeTab === 'users' && searchTerm.length > 0 }
  );

  // Search posts
  const { data: postsData, isLoading: isPostsLoading } = useQuery(
    ['searchPosts', searchTerm],
    () => postService.searchPosts(searchTerm, 0, 30),
    { enabled: activeTab === 'posts' && searchTerm.length > 0 }
  );

  // Search by hashtag
  const { data: hashtagData, isLoading: isHashtagLoading } = useQuery(
    ['searchHashtag', searchTerm],
    () => postService.getPostsByHashtag(searchTerm.replace('#', ''), 0, 30),
    { enabled: activeTab === 'hashtags' && searchTerm.length > 0 }
  );

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setSearchTerm(query);
    setSearchParams({ q: query });
  };

  const handleFollowUser = async (userId: string) => {
    await userService.followUser(userId);
  };

  const handleUnfollowUser = async (userId: string) => {
    await userService.unfollowUser(userId);
  };

  const isLoading = isUsersLoading || isPostsLoading || isHashtagLoading;

  return (
    <div className="mt-16 max-w-2xl mx-auto">
      {/* Search Bar */}
      <form onSubmit={handleSearch} className="mb-6">
        <div className="relative">
          <MagnifyingGlassIcon className="absolute left-4 top-1/2 -translate-y-1/2 h-5 w-5 text-gray-400" />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search users, posts, or #hashtags"
            className="w-full pl-12 pr-4 py-3 bg-gray-100 rounded-lg border-none focus:outline-none focus:ring-2 focus:ring-pink-500"
          />
        </div>
      </form>

      {/* Tabs */}
      <div className="flex border-b border-gray-200 mb-6">
        {(['users', 'posts', 'hashtags'] as SearchTab[]).map((tab) => (
          <button
            key={tab}
            onClick={() => setActiveTab(tab)}
            className={`flex-1 py-3 text-sm font-semibold capitalize transition-colors ${
              activeTab === tab
                ? 'text-gray-900 border-b-2 border-gray-900'
                : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            {tab}
          </button>
        ))}
      </div>

      {/* Results */}
      {!searchTerm ? (
        <div className="text-center py-12">
          <MagnifyingGlassIcon className="h-16 w-16 text-gray-300 mx-auto mb-4" />
          <p className="text-gray-500">Search for users, posts, or hashtags</p>
        </div>
      ) : isLoading ? (
        <div className="flex justify-center py-8">
          <LoadingSpinner size="lg" />
        </div>
      ) : (
        <>
          {/* Users Tab */}
          {activeTab === 'users' && (
            <div className="space-y-2">
              {usersData?.content.length === 0 ? (
                <p className="text-center text-gray-500 py-8">No users found</p>
              ) : (
                usersData?.content.map((user) => (
                  <UserCard
                    key={user.id}
                    user={user}
                    showFollowButton
                    onFollow={handleFollowUser}
                    onUnfollow={handleUnfollowUser}
                  />
                ))
              )}
            </div>
          )}

          {/* Posts Tab */}
          {activeTab === 'posts' && (
            <>
              {postsData?.content.length === 0 ? (
                <p className="text-center text-gray-500 py-8">No posts found</p>
              ) : (
                <div className="grid grid-cols-3 gap-1">
                  {postsData?.content.map((post) => (
                    <Link
                      key={post.id}
                      to={`/post/${post.id}`}
                      className="aspect-square relative group"
                    >
                      <img
                        src={post.imageUrl}
                        alt={post.caption || 'Post'}
                        className="w-full h-full object-cover"
                      />
                      <div className="absolute inset-0 bg-black bg-opacity-0 group-hover:bg-opacity-40 transition-all flex items-center justify-center opacity-0 group-hover:opacity-100">
                        <div className="flex items-center gap-4 text-white font-semibold">
                          <span>❤️ {post.likesCount}</span>
                          <span>💬 {post.commentsCount}</span>
                        </div>
                      </div>
                    </Link>
                  ))}
                </div>
              )}
            </>
          )}

          {/* Hashtags Tab */}
          {activeTab === 'hashtags' && (
            <>
              {hashtagData?.content.length === 0 ? (
                <p className="text-center text-gray-500 py-8">No posts with this hashtag</p>
              ) : (
                <>
                  <p className="text-gray-600 mb-4">
                    {hashtagData?.totalElements} posts for #{searchTerm.replace('#', '')}
                  </p>
                  <div className="grid grid-cols-3 gap-1">
                    {hashtagData?.content.map((post) => (
                      <Link
                        key={post.id}
                        to={`/post/${post.id}`}
                        className="aspect-square relative group"
                      >
                        <img
                          src={post.imageUrl}
                          alt={post.caption || 'Post'}
                          className="w-full h-full object-cover"
                        />
                        <div className="absolute inset-0 bg-black bg-opacity-0 group-hover:bg-opacity-40 transition-all flex items-center justify-center opacity-0 group-hover:opacity-100">
                          <div className="flex items-center gap-4 text-white font-semibold">
                            <span>❤️ {post.likesCount}</span>
                            <span>💬 {post.commentsCount}</span>
                          </div>
                        </div>
                      </Link>
                    ))}
                  </div>
                </>
              )}
            </>
          )}
        </>
      )}
    </div>
  );
};

export default Search;
