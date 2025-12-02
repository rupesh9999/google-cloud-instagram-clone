import { useQuery, useMutation, useQueryClient } from 'react-query';
import { Link } from 'react-router-dom';
import { formatDistanceToNow } from 'date-fns';
import { HeartIcon, ChatBubbleOvalLeftIcon, UserPlusIcon } from '@heroicons/react/24/solid';
import { notificationService } from '../services/notificationService';
import LoadingSpinner from '../components/LoadingSpinner';

const Notifications: React.FC = () => {
  const queryClient = useQueryClient();

  // Fetch notifications
  const { data: notificationsData, isLoading } = useQuery(
    'notifications',
    () => notificationService.getNotifications(0, 50)
  );

  // Mark all as read
  const markAllReadMutation = useMutation(() => notificationService.markAllAsRead(), {
    onSuccess: () => {
      queryClient.invalidateQueries('notifications');
    },
  });

  const getNotificationIcon = (type: string) => {
    switch (type) {
      case 'LIKE':
        return <HeartIcon className="h-6 w-6 text-red-500" />;
      case 'COMMENT':
        return <ChatBubbleOvalLeftIcon className="h-6 w-6 text-blue-500" />;
      case 'FOLLOW':
        return <UserPlusIcon className="h-6 w-6 text-green-500" />;
      default:
        return null;
    }
  };

  if (isLoading) {
    return (
      <div className="flex justify-center items-center h-64 mt-16">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  return (
    <div className="mt-16 max-w-2xl mx-auto">
      <div className="bg-white border border-gray-200 rounded-lg">
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b border-gray-200">
          <h1 className="text-lg font-semibold">Notifications</h1>
          {notificationsData?.content.some((n) => !n.isRead) && (
            <button
              onClick={() => markAllReadMutation.mutate()}
              className="text-blue-500 text-sm font-semibold hover:text-blue-600"
            >
              Mark all as read
            </button>
          )}
        </div>

        {/* Notifications List */}
        {notificationsData?.content.length === 0 ? (
          <div className="p-8 text-center">
            <p className="text-gray-500">No notifications yet</p>
            <p className="text-gray-400 text-sm mt-1">
              When someone likes or comments on your posts, or follows you, you'll see it here.
            </p>
          </div>
        ) : (
          <div className="divide-y divide-gray-100">
            {notificationsData?.content.map((notification) => (
              <Link
                key={notification.id}
                to={
                  notification.type === 'FOLLOW'
                    ? `/profile/${notification.fromUsername}`
                    : `/post/${notification.postId}`
                }
                className={`flex items-center gap-4 p-4 hover:bg-gray-50 transition-colors ${
                  !notification.isRead ? 'bg-blue-50' : ''
                }`}
              >
                {/* User Avatar */}
                <img
                  src={
                    notification.fromUserProfilePicture ||
                    `https://ui-avatars.com/api/?name=${notification.fromUsername}&background=random`
                  }
                  alt={notification.fromUsername}
                  className="h-12 w-12 rounded-full object-cover"
                />

                {/* Content */}
                <div className="flex-1 min-w-0">
                  <p className="text-sm">
                    <span className="font-semibold">{notification.fromUsername}</span>{' '}
                    {notification.message}
                  </p>
                  <p className="text-xs text-gray-400 mt-1">
                    {formatDistanceToNow(new Date(notification.createdAt), { addSuffix: true })}
                  </p>
                </div>

                {/* Icon or Post Thumbnail */}
                {notification.postImageUrl ? (
                  <img
                    src={notification.postImageUrl}
                    alt="Post"
                    className="h-12 w-12 object-cover rounded"
                  />
                ) : (
                  getNotificationIcon(notification.type)
                )}
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default Notifications;
