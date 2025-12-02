package com.instagram.like.service;

import com.instagram.common.exception.BadRequestException;
import com.instagram.like.entity.CommentLike;
import com.instagram.like.entity.PostLike;
import com.instagram.like.repository.CommentLikeRepository;
import com.instagram.like.repository.PostLikeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class LikeService {

    private final PostLikeRepository postLikeRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final RestTemplate restTemplate;

    @Value("${post.service.url}")
    private String postServiceUrl;

    @Value("${comment.service.url}")
    private String commentServiceUrl;

    public void likePost(UUID postId, UUID userId) {
        if (postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            throw new BadRequestException("Post already liked");
        }

        PostLike like = PostLike.builder()
                .postId(postId)
                .userId(userId)
                .build();

        postLikeRepository.save(like);

        // Update post likes count
        try {
            restTemplate.postForEntity(
                    postServiceUrl + "/api/posts/" + postId + "/likes/increment",
                    null,
                    Void.class
            );
        } catch (Exception e) {
            log.warn("Failed to increment likes count for post {}: {}", postId, e.getMessage());
        }

        log.info("User {} liked post {}", userId, postId);
    }

    public void unlikePost(UUID postId, UUID userId) {
        if (!postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            throw new BadRequestException("Post not liked");
        }

        postLikeRepository.deleteByPostIdAndUserId(postId, userId);

        // Update post likes count
        try {
            restTemplate.postForEntity(
                    postServiceUrl + "/api/posts/" + postId + "/likes/decrement",
                    null,
                    Void.class
            );
        } catch (Exception e) {
            log.warn("Failed to decrement likes count for post {}: {}", postId, e.getMessage());
        }

        log.info("User {} unliked post {}", userId, postId);
    }

    @Transactional(readOnly = true)
    public boolean isPostLiked(UUID postId, UUID userId) {
        return postLikeRepository.existsByPostIdAndUserId(postId, userId);
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> getPostLikeStatus(List<UUID> postIds, UUID userId) {
        List<UUID> likedPostIds = postLikeRepository.findLikedPostIds(postIds, userId);
        Set<UUID> likedSet = new HashSet<>(likedPostIds);

        return postIds.stream()
                .collect(Collectors.toMap(
                        UUID::toString,
                        likedSet::contains
                ));
    }

    @Transactional(readOnly = true)
    public long getPostLikesCount(UUID postId) {
        return postLikeRepository.countByPostId(postId);
    }

    public void likeComment(UUID commentId, UUID userId) {
        if (commentLikeRepository.existsByCommentIdAndUserId(commentId, userId)) {
            throw new BadRequestException("Comment already liked");
        }

        CommentLike like = CommentLike.builder()
                .commentId(commentId)
                .userId(userId)
                .build();

        commentLikeRepository.save(like);

        // Update comment likes count
        try {
            restTemplate.postForEntity(
                    commentServiceUrl + "/api/comments/" + commentId + "/likes/increment",
                    null,
                    Void.class
            );
        } catch (Exception e) {
            log.warn("Failed to increment likes count for comment {}: {}", commentId, e.getMessage());
        }

        log.info("User {} liked comment {}", userId, commentId);
    }

    public void unlikeComment(UUID commentId, UUID userId) {
        if (!commentLikeRepository.existsByCommentIdAndUserId(commentId, userId)) {
            throw new BadRequestException("Comment not liked");
        }

        commentLikeRepository.deleteByCommentIdAndUserId(commentId, userId);

        // Update comment likes count
        try {
            restTemplate.postForEntity(
                    commentServiceUrl + "/api/comments/" + commentId + "/likes/decrement",
                    null,
                    Void.class
            );
        } catch (Exception e) {
            log.warn("Failed to decrement likes count for comment {}: {}", commentId, e.getMessage());
        }

        log.info("User {} unliked comment {}", userId, commentId);
    }

    @Transactional(readOnly = true)
    public boolean isCommentLiked(UUID commentId, UUID userId) {
        return commentLikeRepository.existsByCommentIdAndUserId(commentId, userId);
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> getCommentLikeStatus(List<UUID> commentIds, UUID userId) {
        List<UUID> likedCommentIds = commentLikeRepository.findLikedCommentIds(commentIds, userId);
        Set<UUID> likedSet = new HashSet<>(likedCommentIds);

        return commentIds.stream()
                .collect(Collectors.toMap(
                        UUID::toString,
                        likedSet::contains
                ));
    }

    @Transactional(readOnly = true)
    public long getCommentLikesCount(UUID commentId) {
        return commentLikeRepository.countByCommentId(commentId);
    }
}
