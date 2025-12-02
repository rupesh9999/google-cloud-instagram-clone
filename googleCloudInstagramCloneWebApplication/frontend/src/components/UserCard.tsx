import { Link } from 'react-router-dom';
import { User } from '../types';

interface UserCardProps {
  user: User;
  showFollowButton?: boolean;
  onFollow?: (userId: string) => void;
  onUnfollow?: (userId: string) => void;
}

const UserCard: React.FC<UserCardProps> = ({
  user,
  showFollowButton = false,
  onFollow,
  onUnfollow,
}) => {
  const handleFollowClick = () => {
    if (user.isFollowing) {
      onUnfollow?.(user.id);
    } else {
      onFollow?.(user.id);
    }
  };

  return (
    <div className="flex items-center justify-between p-3 hover:bg-gray-50 rounded-lg transition-colors">
      <Link to={`/profile/${user.username}`} className="flex items-center space-x-3 flex-1">
        <img
          src={user.profilePictureUrl || `https://ui-avatars.com/api/?name=${user.username}&background=random`}
          alt={user.username}
          className="h-12 w-12 rounded-full object-cover"
        />
        <div className="flex-1 min-w-0">
          <p className="font-semibold text-sm truncate">{user.username}</p>
          <p className="text-gray-500 text-sm truncate">{user.fullName}</p>
        </div>
      </Link>
      
      {showFollowButton && (
        <button
          onClick={handleFollowClick}
          className={`px-4 py-1.5 rounded-lg text-sm font-semibold transition-colors ${
            user.isFollowing
              ? 'bg-gray-200 text-gray-800 hover:bg-gray-300'
              : 'gradient-btn text-white'
          }`}
        >
          {user.isFollowing ? 'Following' : 'Follow'}
        </button>
      )}
    </div>
  );
};

export default UserCard;
