export interface User {
  id: string;
  username: string;
  email: string;
  fullName: string;
  bio?: string;
  profilePictureUrl?: string;
  followersCount: number;
  followingCount: number;
  postsCount: number;
  isFollowing?: boolean;
  createdAt: string;
}

export interface Post {
  id: string;
  userId: string;
  username: string;
  userProfilePicture?: string;
  imageUrl: string;
  caption?: string;
  hashtags: string[];
  likesCount: number;
  commentsCount: number;
  isLiked: boolean;
  createdAt: string;
}

export interface Comment {
  id: string;
  postId: string;
  userId: string;
  username: string;
  userProfilePicture?: string;
  content: string;
  createdAt: string;
}

export interface Like {
  id: string;
  postId: string;
  userId: string;
  username: string;
  createdAt: string;
}

export interface Notification {
  id: string;
  type: 'LIKE' | 'COMMENT' | 'FOLLOW';
  fromUserId: string;
  fromUsername: string;
  fromUserProfilePicture?: string;
  postId?: string;
  postImageUrl?: string;
  message: string;
  isRead: boolean;
  createdAt: string;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface PaginatedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface ApiError {
  message: string;
  status: number;
  errors?: Record<string, string>;
}
