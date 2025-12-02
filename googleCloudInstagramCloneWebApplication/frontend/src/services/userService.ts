import api from './api';
import { User, PaginatedResponse } from '../types';

export interface UpdateProfileRequest {
  fullName?: string;
  bio?: string;
  profilePicture?: File;
}

export const userService = {
  async getProfile(username: string): Promise<User> {
    const response = await api.get<User>(`/users/${username}`);
    return response.data;
  },

  async getCurrentUser(): Promise<User> {
    const response = await api.get<User>('/users/me');
    return response.data;
  },

  async updateProfile(data: UpdateProfileRequest): Promise<User> {
    const formData = new FormData();
    if (data.fullName) formData.append('fullName', data.fullName);
    if (data.bio) formData.append('bio', data.bio);
    if (data.profilePicture) formData.append('profilePicture', data.profilePicture);

    const response = await api.put<User>('/users/me', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data;
  },

  async followUser(userId: string): Promise<void> {
    await api.post(`/users/${userId}/follow`);
  },

  async unfollowUser(userId: string): Promise<void> {
    await api.delete(`/users/${userId}/follow`);
  },

  async getFollowers(username: string, page = 0, size = 20): Promise<PaginatedResponse<User>> {
    const response = await api.get<PaginatedResponse<User>>(`/users/${username}/followers`, {
      params: { page, size },
    });
    return response.data;
  },

  async getFollowing(username: string, page = 0, size = 20): Promise<PaginatedResponse<User>> {
    const response = await api.get<PaginatedResponse<User>>(`/users/${username}/following`, {
      params: { page, size },
    });
    return response.data;
  },

  async searchUsers(query: string, page = 0, size = 20): Promise<PaginatedResponse<User>> {
    const response = await api.get<PaginatedResponse<User>>('/users/search', {
      params: { query, page, size },
    });
    return response.data;
  },

  async getSuggestedUsers(limit = 5): Promise<User[]> {
    const response = await api.get<User[]>('/users/suggestions', {
      params: { limit },
    });
    return response.data;
  },
};
