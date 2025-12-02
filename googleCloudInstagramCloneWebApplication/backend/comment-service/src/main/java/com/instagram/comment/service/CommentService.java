package com.instagram.comment.service;

import com.instagram.comment.entity.Comment;
import com.instagram.comment.repository.CommentRepository;
import com.instagram.common.dto.CommentDto;
import com.instagram.common.dto.PagedResponse;
import com.instagram.common.dto.UserDto;
import com.instagram.common.exception.BadRequestException;
import com.instagram.common.exception.ResourceNotFoundException;
import com.instagram.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CommentService {

    private final CommentRepository commentRepository;
    private final RestTemplate restTemplate;

    @Value("${user.service.url}")
    private String userServiceUrl;

    @Value("${post.service.url}")
    private String postServiceUrl;

    public CommentDto createComment(UUID postId, UUID userId, String content, UUID parentId) {
        if (content == null || content.isBlank()) {
            throw new BadRequestException("Comment content cannot be empty");
        }

        // Verify post exists
        if (!postExists(postId)) {
            throw new ResourceNotFoundException("Post", "id", postId.toString());
        }

        // Verify parent comment exists if replying
        if (parentId != null) {
            commentRepository.findByIdAndIsActiveTrue(parentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Comment", "id", parentId.toString()));
        }

        Comment comment = Comment.builder()
                .postId(postId)
                .userId(userId)
                .parentId(parentId)
                .content(content)
                .build();

        comment = commentRepository.save(comment);

        // Update post comments count
        try {
            restTemplate.postForEntity(
                    postServiceUrl + "/api/posts/" + postId + "/comments/increment",
                    null,
                    Void.class
            );
        } catch (Exception e) {
            log.warn("Failed to increment comments count for post {}: {}", postId, e.getMessage());
        }

        log.info("Created comment {} on post {} by user {}", comment.getId(), postId, userId);
        return mapToDto(comment, fetchUser(userId));
    }

    @Transactional(readOnly = true)
    public CommentDto getComment(UUID commentId) {
        Comment comment = commentRepository.findByIdAndIsActiveTrue(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", "id", commentId.toString()));

        UserDto user = fetchUser(comment.getUserId());
        return mapToDto(comment, user);
    }

    public CommentDto updateComment(UUID commentId, UUID userId, String content) {
        Comment comment = commentRepository.findByIdAndIsActiveTrue(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", "id", commentId.toString()));

        if (!comment.getUserId().equals(userId)) {
            throw new UnauthorizedException("You can only update your own comments");
        }

        if (content == null || content.isBlank()) {
            throw new BadRequestException("Comment content cannot be empty");
        }

        comment.setContent(content);
        comment = commentRepository.save(comment);

        log.info("Updated comment {}", commentId);
        return mapToDto(comment, fetchUser(userId));
    }

    public void deleteComment(UUID commentId, UUID userId) {
        Comment comment = commentRepository.findByIdAndIsActiveTrue(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", "id", commentId.toString()));

        if (!comment.getUserId().equals(userId)) {
            throw new UnauthorizedException("You can only delete your own comments");
        }

        comment.setIsActive(false);
        commentRepository.save(comment);

        // Update post comments count
        try {
            restTemplate.postForEntity(
                    postServiceUrl + "/api/posts/" + comment.getPostId() + "/comments/decrement",
                    null,
                    Void.class
            );
        } catch (Exception e) {
            log.warn("Failed to decrement comments count for post {}: {}", comment.getPostId(), e.getMessage());
        }

        log.info("Deleted comment {}", commentId);
    }

    @Transactional(readOnly = true)
    public PagedResponse<CommentDto> getPostComments(UUID postId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Comment> commentsPage = commentRepository.findTopLevelCommentsByPostId(postId, pageable);

        Set<UUID> userIds = commentsPage.getContent().stream()
                .map(Comment::getUserId)
                .collect(Collectors.toSet());

        Map<UUID, UserDto> usersMap = fetchUsers(userIds);

        List<CommentDto> comments = commentsPage.getContent().stream()
                .map(comment -> mapToDto(comment, usersMap.get(comment.getUserId())))
                .toList();

        return PagedResponse.of(
                comments,
                commentsPage.getNumber(),
                commentsPage.getSize(),
                commentsPage.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<CommentDto> getCommentReplies(UUID commentId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Comment> repliesPage = commentRepository.findRepliesByParentId(commentId, pageable);

        Set<UUID> userIds = repliesPage.getContent().stream()
                .map(Comment::getUserId)
                .collect(Collectors.toSet());

        Map<UUID, UserDto> usersMap = fetchUsers(userIds);

        List<CommentDto> replies = repliesPage.getContent().stream()
                .map(reply -> mapToDto(reply, usersMap.get(reply.getUserId())))
                .toList();

        return PagedResponse.of(
                replies,
                repliesPage.getNumber(),
                repliesPage.getSize(),
                repliesPage.getTotalElements()
        );
    }

    public void incrementLikesCount(UUID commentId) {
        commentRepository.incrementLikesCount(commentId);
    }

    public void decrementLikesCount(UUID commentId) {
        commentRepository.decrementLikesCount(commentId);
    }

    @Transactional(readOnly = true)
    public Optional<UUID> getCommentOwnerId(UUID commentId) {
        return commentRepository.findUserIdByCommentId(commentId);
    }

    @Transactional(readOnly = true)
    public Optional<UUID> getCommentPostId(UUID commentId) {
        return commentRepository.findPostIdByCommentId(commentId);
    }

    private boolean postExists(UUID postId) {
        try {
            ResponseEntity<Boolean> response = restTemplate.getForEntity(
                    postServiceUrl + "/api/posts/" + postId + "/exists",
                    Boolean.class
            );
            return Boolean.TRUE.equals(response.getBody());
        } catch (Exception e) {
            log.warn("Failed to check if post {} exists: {}", postId, e.getMessage());
            return false;
        }
    }

    private UserDto fetchUser(UUID userId) {
        try {
            ResponseEntity<UserDto> response = restTemplate.getForEntity(
                    userServiceUrl + "/api/users/id/" + userId,
                    UserDto.class
            );
            return response.getBody();
        } catch (Exception e) {
            log.warn("Failed to fetch user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    private Map<UUID, UserDto> fetchUsers(Set<UUID> userIds) {
        Map<UUID, UserDto> usersMap = new HashMap<>();
        for (UUID userId : userIds) {
            UserDto user = fetchUser(userId);
            if (user != null) {
                usersMap.put(userId, user);
            }
        }
        return usersMap;
    }

    private CommentDto mapToDto(Comment comment, UserDto user) {
        return CommentDto.builder()
                .id(comment.getId().toString())
                .postId(comment.getPostId().toString())
                .userId(comment.getUserId().toString())
                .user(user)
                .parentId(comment.getParentId() != null ? comment.getParentId().toString() : null)
                .content(comment.getContent())
                .likesCount(comment.getLikesCount())
                .repliesCount(comment.getRepliesCount())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
