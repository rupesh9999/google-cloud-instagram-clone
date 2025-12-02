package com.instagram.like.controller;

import com.instagram.like.service.LikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/likes")
@RequiredArgsConstructor
@Tag(name = "Likes", description = "Like management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class LikeController {

    private final LikeService likeService;

    // Post likes

    @PostMapping("/posts/{postId}")
    @Operation(summary = "Like a post")
    public ResponseEntity<Void> likePost(
            @PathVariable UUID postId,
            @RequestHeader("X-User-Id") String userId) {
        likeService.likePost(postId, UUID.fromString(userId));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/posts/{postId}")
    @Operation(summary = "Unlike a post")
    public ResponseEntity<Void> unlikePost(
            @PathVariable UUID postId,
            @RequestHeader("X-User-Id") String userId) {
        likeService.unlikePost(postId, UUID.fromString(userId));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/posts/{postId}/status")
    @Operation(summary = "Check if user liked a post")
    public ResponseEntity<Boolean> isPostLiked(
            @PathVariable UUID postId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(likeService.isPostLiked(postId, UUID.fromString(userId)));
    }

    @PostMapping("/posts/status")
    @Operation(summary = "Get like status for multiple posts (internal use)")
    public ResponseEntity<Map<String, Boolean>> getPostLikeStatus(
            @RequestBody List<UUID> postIds,
            @RequestParam UUID userId) {
        return ResponseEntity.ok(likeService.getPostLikeStatus(postIds, userId));
    }

    @GetMapping("/posts/{postId}/count")
    @Operation(summary = "Get post likes count")
    public ResponseEntity<Long> getPostLikesCount(@PathVariable UUID postId) {
        return ResponseEntity.ok(likeService.getPostLikesCount(postId));
    }

    // Comment likes

    @PostMapping("/comments/{commentId}")
    @Operation(summary = "Like a comment")
    public ResponseEntity<Void> likeComment(
            @PathVariable UUID commentId,
            @RequestHeader("X-User-Id") String userId) {
        likeService.likeComment(commentId, UUID.fromString(userId));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/comments/{commentId}")
    @Operation(summary = "Unlike a comment")
    public ResponseEntity<Void> unlikeComment(
            @PathVariable UUID commentId,
            @RequestHeader("X-User-Id") String userId) {
        likeService.unlikeComment(commentId, UUID.fromString(userId));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/comments/{commentId}/status")
    @Operation(summary = "Check if user liked a comment")
    public ResponseEntity<Boolean> isCommentLiked(
            @PathVariable UUID commentId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(likeService.isCommentLiked(commentId, UUID.fromString(userId)));
    }

    @PostMapping("/comments/status")
    @Operation(summary = "Get like status for multiple comments (internal use)")
    public ResponseEntity<Map<String, Boolean>> getCommentLikeStatus(
            @RequestBody List<UUID> commentIds,
            @RequestParam UUID userId) {
        return ResponseEntity.ok(likeService.getCommentLikeStatus(commentIds, userId));
    }

    @GetMapping("/comments/{commentId}/count")
    @Operation(summary = "Get comment likes count")
    public ResponseEntity<Long> getCommentLikesCount(@PathVariable UUID commentId) {
        return ResponseEntity.ok(likeService.getCommentLikesCount(commentId));
    }
}
