import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery } from 'react-query';
import { Cog6ToothIcon } from '@heroicons/react/24/outline';
import { userService } from '../services/userService';
import { postService } from '../services/postService';
import { useAuthStore } from '../hooks/useAuthStore';
import LoadingSpinner from '../components/LoadingSpinner';
import toast from 'react-hot-toast';

const Profile: React.FC = () => {
  const { username } = useParams<{ username: string }>();
  const { user: currentUser } = useAuthStore();
  const isOwnProfile = currentUser?.username === username;

  const [activeTab, setActiveTab] = useState<'posts' | 'saved'>('posts');

  // Fetch user profile
  const {
    data: user,
    isLoading: isUserLoading,
    refetch: refetchUser,
  } = useQuery(['user', username], () => userService.getProfile(username!), {
    enabled: !!username,
  });

  // Fetch user posts
  const { data: postsData, isLoading: isPostsLoading } = useQuery(
    ['userPosts', username],
    () => postService.getUserPosts(username!, 0, 30),
    { enabled: !!username }
  );

  const handleFollow = async () => {
    if (!user) return;
    try {
      if (user.isFollowing) {
        await userService.unfollowUser(user.id);
        toast.success(`Unfollowed ${user.username}`);
      } else {
        await userService.followUser(user.id);
        toast.success(`Following ${user.username}`);
      }
      refetchUser();
    } catch {
      toast.error('Action failed');
    }
  };

  if (isUserLoading) {
    return (
      <div className="flex justify-center items-center h-64 mt-16">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  if (!user) {
    return (
      <div className="mt-16 text-center py-8">
        <h2 className="text-2xl font-semibold">User not found</h2>
      </div>
    );
  }

  return (
    <div className="mt-16 max-w-4xl mx-auto px-4">
      {/* Profile Header */}
      <header className="flex flex-col md:flex-row items-center md:items-start gap-8 mb-8">
        {/* Profile Picture */}
        <div className="story-ring p-1">
          <img
            src={user.profilePictureUrl || `https://ui-avatars.com/api/?name=${user.username}&background=random&size=150`}
            alt={user.username}
            className="w-32 h-32 md:w-40 md:h-40 rounded-full object-cover"
          />
        </div>

        {/* Profile Info */}
        <div className="flex-1 text-center md:text-left">
          <div className="flex flex-col md:flex-row items-center gap-4 mb-4">
            <h1 className="text-xl font-normal">{user.username}</h1>
            {isOwnProfile ? (
              <div className="flex gap-2">
                <Link
                  to="/edit-profile"
                  className="px-4 py-1.5 bg-gray-100 hover:bg-gray-200 rounded-lg font-semibold text-sm transition-colors"
                >
                  Edit profile
                </Link>
                <button className="p-1.5 hover:bg-gray-100 rounded-lg">
                  <Cog6ToothIcon className="h-6 w-6" />
                </button>
              </div>
            ) : (
              <button
                onClick={handleFollow}
                className={`px-6 py-1.5 rounded-lg font-semibold text-sm transition-colors ${
                  user.isFollowing
                    ? 'bg-gray-200 text-gray-800 hover:bg-gray-300'
                    : 'gradient-btn text-white'
                }`}
              >
                {user.isFollowing ? 'Following' : 'Follow'}
              </button>
            )}
          </div>

          {/* Stats */}
          <div className="flex justify-center md:justify-start gap-8 mb-4">
            <div className="text-center md:text-left">
              <span className="font-semibold">{user.postsCount}</span>
              <span className="text-gray-600 ml-1">posts</span>
            </div>
            <button className="text-center md:text-left hover:opacity-60">
              <span className="font-semibold">{user.followersCount}</span>
              <span className="text-gray-600 ml-1">followers</span>
            </button>
            <button className="text-center md:text-left hover:opacity-60">
              <span className="font-semibold">{user.followingCount}</span>
              <span className="text-gray-600 ml-1">following</span>
            </button>
          </div>

          {/* Bio */}
          <div>
            <p className="font-semibold">{user.fullName}</p>
            {user.bio && <p className="text-gray-700 whitespace-pre-wrap">{user.bio}</p>}
          </div>
        </div>
      </header>

      {/* Tabs */}
      <div className="border-t border-gray-200">
        <div className="flex justify-center gap-12">
          <button
            onClick={() => setActiveTab('posts')}
            className={`py-4 text-xs font-semibold tracking-widest uppercase border-t ${
              activeTab === 'posts'
                ? 'border-gray-900 text-gray-900'
                : 'border-transparent text-gray-400'
            }`}
          >
            Posts
          </button>
          {isOwnProfile && (
            <button
              onClick={() => setActiveTab('saved')}
              className={`py-4 text-xs font-semibold tracking-widest uppercase border-t ${
                activeTab === 'saved'
                  ? 'border-gray-900 text-gray-900'
                  : 'border-transparent text-gray-400'
              }`}
            >
              Saved
            </button>
          )}
        </div>
      </div>

      {/* Posts Grid */}
      {isPostsLoading ? (
        <div className="flex justify-center py-8">
          <LoadingSpinner size="lg" />
        </div>
      ) : postsData?.content.length === 0 ? (
        <div className="py-16 text-center">
          <h3 className="text-xl font-semibold mb-2">No Posts Yet</h3>
          <p className="text-gray-500">
            {isOwnProfile
              ? "When you share photos, they'll appear here."
              : "This user hasn't posted anything yet."}
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-3 gap-1 md:gap-6 py-4">
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
                <div className="flex items-center gap-6 text-white font-semibold">
                  <span>❤️ {post.likesCount}</span>
                  <span>💬 {post.commentsCount}</span>
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
};

export default Profile;
