import api from './api';
import { Post, PaginatedResponse } from '../types';

export const feedService = {
  async getFeed(page = 0, size = 10): Promise<PaginatedResponse<Post>> {
    const response = await api.get<PaginatedResponse<Post>>('/feed', {
      params: { page, size },
    });
    return response.data;
  },

  async getExplorePosts(page = 0, size = 20): Promise<PaginatedResponse<Post>> {
    const response = await api.get<PaginatedResponse<Post>>('/feed/explore', {
      params: { page, size },
    });
    return response.data;
  },
};
