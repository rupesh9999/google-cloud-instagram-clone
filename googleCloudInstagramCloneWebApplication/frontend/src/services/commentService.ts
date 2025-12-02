import api from './api';
import { Comment, PaginatedResponse } from '../types';

export interface CreateCommentRequest {
  postId: string;
  content: string;
}

export const commentService = {
  async createComment(data: CreateCommentRequest): Promise<Comment> {
    const response = await api.post<Comment>('/comments', data);
    return response.data;
  },

  async getPostComments(postId: string, page = 0, size = 20): Promise<PaginatedResponse<Comment>> {
    const response = await api.get<PaginatedResponse<Comment>>(`/comments/post/${postId}`, {
      params: { page, size },
    });
    return response.data;
  },

  async deleteComment(commentId: string): Promise<void> {
    await api.delete(`/comments/${commentId}`);
  },
};
