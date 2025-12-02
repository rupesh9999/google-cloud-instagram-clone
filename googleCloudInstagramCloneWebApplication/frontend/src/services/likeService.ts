import api from './api';
import { User, PaginatedResponse } from '../types';

export const likeService = {
  async likePost(postId: string): Promise<void> {
    await api.post(`/likes/post/${postId}`);
  },

  async unlikePost(postId: string): Promise<void> {
    await api.delete(`/likes/post/${postId}`);
  },

  async getPostLikes(postId: string, page = 0, size = 20): Promise<PaginatedResponse<User>> {
    const response = await api.get<PaginatedResponse<User>>(`/likes/post/${postId}`, {
      params: { page, size },
    });
    return response.data;
  },

  async isPostLiked(postId: string): Promise<boolean> {
    const response = await api.get<{ liked: boolean }>(`/likes/post/${postId}/status`);
    return response.data.liked;
  },
};
