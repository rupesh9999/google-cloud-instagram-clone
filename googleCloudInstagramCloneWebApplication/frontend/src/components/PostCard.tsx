import { useState } from 'react';
import { Link } from 'react-router-dom';
import { HeartIcon, ChatBubbleOvalLeftIcon, BookmarkIcon, EllipsisHorizontalIcon } from '@heroicons/react/24/outline';
import { HeartIcon as HeartIconSolid } from '@heroicons/react/24/solid';
import { Post } from '../types';
import { likeService } from '../services/likeService';
import { formatDistanceToNow } from 'date-fns';

interface PostCardProps {
  post: Post;
  onLikeToggle?: (postId: string, isLiked: boolean) => void;
}

const PostCard: React.FC<PostCardProps> = ({ post, onLikeToggle }) => {
  const [isLiked, setIsLiked] = useState(post.isLiked);
  const [likesCount, setLikesCount] = useState(post.likesCount);
  const [isLoading, setIsLoading] = useState(false);

  const handleLikeToggle = async () => {
    if (isLoading) return;
    
    setIsLoading(true);
    const previousLiked = isLiked;
    const previousCount = likesCount;

    // Optimistic update
    setIsLiked(!isLiked);
    setLikesCount(isLiked ? likesCount - 1 : likesCount + 1);

    try {
      if (previousLiked) {
        await likeService.unlikePost(post.id);
      } else {
        await likeService.likePost(post.id);
      }
      onLikeToggle?.(post.id, !previousLiked);
    } catch {
      // Revert on error
      setIsLiked(previousLiked);
      setLikesCount(previousCount);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <article className="post-card">
      {/* Header */}
      <header className="flex items-center justify-between p-4">
        <Link to={`/profile/${post.username}`} className="flex items-center space-x-3">
          <img
            src={post.userProfilePicture || `https://ui-avatars.com/api/?name=${post.username}&background=random`}
            alt={post.username}
            className="h-10 w-10 rounded-full object-cover"
          />
          <span className="font-semibold text-sm">{post.username}</span>
        </Link>
        <button className="p-1 hover:bg-gray-100 rounded-full">
          <EllipsisHorizontalIcon className="h-6 w-6" />
        </button>
      </header>

      {/* Image */}
      <Link to={`/post/${post.id}`}>
        <img
          src={post.imageUrl}
          alt={post.caption || 'Post image'}
          className="w-full aspect-square object-cover"
        />
      </Link>

      {/* Actions */}
      <div className="p-4">
        <div className="flex items-center justify-between mb-2">
          <div className="flex items-center space-x-4">
            <button
              onClick={handleLikeToggle}
              disabled={isLoading}
              className="p-1 hover:opacity-60 transition-opacity"
            >
              {isLiked ? (
                <HeartIconSolid className="h-7 w-7 text-red-500" />
              ) : (
                <HeartIcon className="h-7 w-7" />
              )}
            </button>
            <Link to={`/post/${post.id}`} className="p-1 hover:opacity-60 transition-opacity">
              <ChatBubbleOvalLeftIcon className="h-7 w-7" />
            </Link>
          </div>
          <button className="p-1 hover:opacity-60 transition-opacity">
            <BookmarkIcon className="h-7 w-7" />
          </button>
        </div>

        {/* Likes count */}
        <p className="font-semibold text-sm mb-1">
          {likesCount.toLocaleString()} {likesCount === 1 ? 'like' : 'likes'}
        </p>

        {/* Caption */}
        {post.caption && (
          <p className="text-sm mb-1">
            <Link to={`/profile/${post.username}`} className="font-semibold mr-1">
              {post.username}
            </Link>
            {post.caption}
          </p>
        )}

        {/* Hashtags */}
        {post.hashtags.length > 0 && (
          <p className="text-sm text-blue-900">
            {post.hashtags.map((tag) => (
              <Link key={tag} to={`/search?hashtag=${tag}`} className="mr-1">
                #{tag}
              </Link>
            ))}
          </p>
        )}

        {/* Comments link */}
        {post.commentsCount > 0 && (
          <Link to={`/post/${post.id}`} className="text-sm text-gray-500 mt-1 block">
            View all {post.commentsCount} comments
          </Link>
        )}

        {/* Timestamp */}
        <p className="text-xs text-gray-400 mt-2 uppercase">
          {formatDistanceToNow(new Date(post.createdAt), { addSuffix: true })}
        </p>
      </div>
    </article>
  );
};

export default PostCard;
