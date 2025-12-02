import api from './api';
import { Post, PaginatedResponse } from '../types';

export interface CreatePostRequest {
  image: File;
  caption?: string;
  hashtags?: string[];
}

export const postService = {
  async createPost(data: CreatePostRequest): Promise<Post> {
    const formData = new FormData();
    formData.append('image', data.image);
    if (data.caption) formData.append('caption', data.caption);
    if (data.hashtags?.length) {
      data.hashtags.forEach((tag) => formData.append('hashtags', tag));
    }

    const response = await api.post<Post>('/posts', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data;
  },

  async getPost(postId: string): Promise<Post> {
    const response = await api.get<Post>(`/posts/${postId}`);
    return response.data;
  },

  async getUserPosts(username: string, page = 0, size = 12): Promise<PaginatedResponse<Post>> {
    const response = await api.get<PaginatedResponse<Post>>(`/posts/user/${username}`, {
      params: { page, size },
    });
    return response.data;
  },

  async deletePost(postId: string): Promise<void> {
    await api.delete(`/posts/${postId}`);
  },

  async searchPosts(query: string, page = 0, size = 20): Promise<PaginatedResponse<Post>> {
    const response = await api.get<PaginatedResponse<Post>>('/posts/search', {
      params: { query, page, size },
    });
    return response.data;
  },

  async getPostsByHashtag(hashtag: string, page = 0, size = 20): Promise<PaginatedResponse<Post>> {
    const response = await api.get<PaginatedResponse<Post>>(`/posts/hashtag/${hashtag}`, {
      params: { page, size },
    });
    return response.data;
  },
};
