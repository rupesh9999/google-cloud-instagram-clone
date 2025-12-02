package com.instagram.comment.controller;

import com.instagram.comment.service.CommentService;
import com.instagram.common.dto.CommentDto;
import com.instagram.common.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
@Tag(name = "Comments", description = "Comment management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/post/{postId}")
    @Operation(summary = "Create a comment on a post")
    public ResponseEntity<CommentDto> createComment(
            @PathVariable UUID postId,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, String> request) {
        String content = request.get("content");
        String parentIdStr = request.get("parentId");
        UUID parentId = parentIdStr != null ? UUID.fromString(parentIdStr) : null;

        return ResponseEntity.ok(commentService.createComment(
                postId,
                UUID.fromString(userId),
                content,
                parentId
        ));
    }

    @GetMapping("/{commentId}")
    @Operation(summary = "Get a comment by ID")
    public ResponseEntity<CommentDto> getComment(@PathVariable UUID commentId) {
        return ResponseEntity.ok(commentService.getComment(commentId));
    }

    @PutMapping("/{commentId}")
    @Operation(summary = "Update a comment")
    public ResponseEntity<CommentDto> updateComment(
            @PathVariable UUID commentId,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, String> request) {
        return ResponseEntity.ok(commentService.updateComment(
                commentId,
                UUID.fromString(userId),
                request.get("content")
        ));
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "Delete a comment")
    public ResponseEntity<Void> deleteComment(
            @PathVariable UUID commentId,
            @RequestHeader("X-User-Id") String userId) {
        commentService.deleteComment(commentId, UUID.fromString(userId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/post/{postId}")
    @Operation(summary = "Get comments for a post")
    public ResponseEntity<PagedResponse<CommentDto>> getPostComments(
            @PathVariable UUID postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(commentService.getPostComments(postId, page, size));
    }

    @GetMapping("/{commentId}/replies")
    @Operation(summary = "Get replies for a comment")
    public ResponseEntity<PagedResponse<CommentDto>> getCommentReplies(
            @PathVariable UUID commentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(commentService.getCommentReplies(commentId, page, size));
    }

    @PostMapping("/{commentId}/likes/increment")
    @Operation(summary = "Increment comment likes count (internal use)")
    public ResponseEntity<Void> incrementLikesCount(@PathVariable UUID commentId) {
        commentService.incrementLikesCount(commentId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{commentId}/likes/decrement")
    @Operation(summary = "Decrement comment likes count (internal use)")
    public ResponseEntity<Void> decrementLikesCount(@PathVariable UUID commentId) {
        commentService.decrementLikesCount(commentId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{commentId}/owner")
    @Operation(summary = "Get comment owner ID (internal use)")
    public ResponseEntity<UUID> getCommentOwnerId(@PathVariable UUID commentId) {
        Optional<UUID> ownerId = commentService.getCommentOwnerId(commentId);
        return ownerId.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{commentId}/post")
    @Operation(summary = "Get comment's post ID (internal use)")
    public ResponseEntity<UUID> getCommentPostId(@PathVariable UUID commentId) {
        Optional<UUID> postId = commentService.getCommentPostId(commentId);
        return postId.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
