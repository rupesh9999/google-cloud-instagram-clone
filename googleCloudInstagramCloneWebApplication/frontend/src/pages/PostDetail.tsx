import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from 'react-query';
import { HeartIcon, ChatBubbleOvalLeftIcon, BookmarkIcon, EllipsisHorizontalIcon } from '@heroicons/react/24/outline';
import { HeartIcon as HeartIconSolid } from '@heroicons/react/24/solid';
import { formatDistanceToNow } from 'date-fns';
import { postService } from '../services/postService';
import { commentService, CreateCommentRequest } from '../services/commentService';
import { likeService } from '../services/likeService';
import { useAuthStore } from '../hooks/useAuthStore';
import LoadingSpinner from '../components/LoadingSpinner';
import toast from 'react-hot-toast';

const PostDetail: React.FC = () => {
  const { postId } = useParams<{ postId: string }>();
  const queryClient = useQueryClient();
  const { user: currentUser } = useAuthStore();
  const [commentText, setCommentText] = useState('');

  // Fetch post
  const { data: post, isLoading: isPostLoading } = useQuery(
    ['post', postId],
    () => postService.getPost(postId!),
    { enabled: !!postId }
  );

  // Fetch comments
  const { data: commentsData, isLoading: isCommentsLoading } = useQuery(
    ['comments', postId],
    () => commentService.getPostComments(postId!, 0, 50),
    { enabled: !!postId }
  );

  // Like mutation
  const likeMutation = useMutation(
    () => (post?.isLiked ? likeService.unlikePost(postId!) : likeService.likePost(postId!)),
    {
      onSuccess: () => {
        queryClient.invalidateQueries(['post', postId]);
      },
    }
  );

  // Comment mutation
  const commentMutation = useMutation(
    (data: CreateCommentRequest) => commentService.createComment(data),
    {
      onSuccess: () => {
        queryClient.invalidateQueries(['comments', postId]);
        queryClient.invalidateQueries(['post', postId]);
        setCommentText('');
        toast.success('Comment added!');
      },
      onError: () => {
        toast.error('Failed to add comment');
      },
    }
  );

  const handleLike = () => {
    likeMutation.mutate();
  };

  const handleComment = (e: React.FormEvent) => {
    e.preventDefault();
    if (!commentText.trim()) return;
    commentMutation.mutate({ postId: postId!, content: commentText.trim() });
  };

  if (isPostLoading) {
    return (
      <div className="flex justify-center items-center h-64 mt-16">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  if (!post) {
    return (
      <div className="mt-16 text-center py-8">
        <h2 className="text-2xl font-semibold">Post not found</h2>
      </div>
    );
  }

  return (
    <div className="mt-16 max-w-4xl mx-auto">
      <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
        <div className="flex flex-col md:flex-row">
          {/* Image */}
          <div className="md:w-2/3">
            <img
              src={post.imageUrl}
              alt={post.caption || 'Post'}
              className="w-full h-full object-cover"
            />
          </div>

          {/* Details */}
          <div className="md:w-1/3 flex flex-col">
            {/* Header */}
            <div className="flex items-center justify-between p-4 border-b border-gray-200">
              <Link to={`/profile/${post.username}`} className="flex items-center gap-3">
                <img
                  src={post.userProfilePicture || `https://ui-avatars.com/api/?name=${post.username}&background=random`}
                  alt={post.username}
                  className="h-8 w-8 rounded-full object-cover"
                />
                <span className="font-semibold text-sm">{post.username}</span>
              </Link>
              <button className="p-1 hover:bg-gray-100 rounded-full">
                <EllipsisHorizontalIcon className="h-6 w-6" />
              </button>
            </div>

            {/* Comments Section */}
            <div className="flex-1 overflow-y-auto p-4 space-y-4 max-h-96">
              {/* Caption */}
              {post.caption && (
                <div className="flex gap-3">
                  <Link to={`/profile/${post.username}`}>
                    <img
                      src={post.userProfilePicture || `https://ui-avatars.com/api/?name=${post.username}&background=random`}
                      alt={post.username}
                      className="h-8 w-8 rounded-full object-cover"
                    />
                  </Link>
                  <div>
                    <p className="text-sm">
                      <Link to={`/profile/${post.username}`} className="font-semibold mr-1">
                        {post.username}
                      </Link>
                      {post.caption}
                    </p>
                    {post.hashtags.length > 0 && (
                      <p className="text-sm text-blue-900 mt-1">
                        {post.hashtags.map((tag) => `#${tag}`).join(' ')}
                      </p>
                    )}
                    <p className="text-xs text-gray-400 mt-1">
                      {formatDistanceToNow(new Date(post.createdAt), { addSuffix: true })}
                    </p>
                  </div>
                </div>
              )}

              {/* Comments */}
              {isCommentsLoading ? (
                <LoadingSpinner size="sm" />
              ) : (
                commentsData?.content.map((comment) => (
                  <div key={comment.id} className="flex gap-3">
                    <Link to={`/profile/${comment.username}`}>
                      <img
                        src={comment.userProfilePicture || `https://ui-avatars.com/api/?name=${comment.username}&background=random`}
                        alt={comment.username}
                        className="h-8 w-8 rounded-full object-cover"
                      />
                    </Link>
                    <div className="flex-1">
                      <p className="text-sm">
                        <Link to={`/profile/${comment.username}`} className="font-semibold mr-1">
                          {comment.username}
                        </Link>
                        {comment.content}
                      </p>
                      <p className="text-xs text-gray-400 mt-1">
                        {formatDistanceToNow(new Date(comment.createdAt), { addSuffix: true })}
                      </p>
                    </div>
                  </div>
                ))
              )}
            </div>

            {/* Actions */}
            <div className="border-t border-gray-200 p-4">
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-4">
                  <button
                    onClick={handleLike}
                    disabled={likeMutation.isLoading}
                    className="p-1 hover:opacity-60"
                  >
                    {post.isLiked ? (
                      <HeartIconSolid className="h-7 w-7 text-red-500" />
                    ) : (
                      <HeartIcon className="h-7 w-7" />
                    )}
                  </button>
                  <button className="p-1 hover:opacity-60">
                    <ChatBubbleOvalLeftIcon className="h-7 w-7" />
                  </button>
                </div>
                <button className="p-1 hover:opacity-60">
                  <BookmarkIcon className="h-7 w-7" />
                </button>
              </div>

              <p className="font-semibold text-sm mb-2">
                {post.likesCount.toLocaleString()} {post.likesCount === 1 ? 'like' : 'likes'}
              </p>
              <p className="text-xs text-gray-400 uppercase">
                {formatDistanceToNow(new Date(post.createdAt), { addSuffix: true })}
              </p>
            </div>

            {/* Comment Input */}
            <form onSubmit={handleComment} className="border-t border-gray-200 p-4 flex gap-2">
              <img
                src={currentUser?.profilePictureUrl || `https://ui-avatars.com/api/?name=${currentUser?.username}&background=random`}
                alt={currentUser?.username}
                className="h-8 w-8 rounded-full object-cover"
              />
              <input
                type="text"
                value={commentText}
                onChange={(e) => setCommentText(e.target.value)}
                placeholder="Add a comment..."
                className="flex-1 border-none focus:outline-none text-sm"
              />
              <button
                type="submit"
                disabled={!commentText.trim() || commentMutation.isLoading}
                className="text-blue-500 font-semibold text-sm disabled:opacity-50"
              >
                Post
              </button>
            </form>
          </div>
        </div>
      </div>
    </div>
  );
};

export default PostDetail;
