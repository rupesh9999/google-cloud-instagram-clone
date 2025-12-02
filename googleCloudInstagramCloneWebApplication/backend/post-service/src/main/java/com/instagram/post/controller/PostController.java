package com.instagram.post.controller;

import com.instagram.common.dto.PagedResponse;
import com.instagram.common.dto.PostDto;
import com.instagram.post.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Tag(name = "Posts", description = "Post management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class PostController {

    private final PostService postService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create a new post")
    public ResponseEntity<PostDto> createPost(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(value = "caption", required = false) String caption,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam("images") List<MultipartFile> images) throws IOException {
        return ResponseEntity.ok(postService.createPost(
                UUID.fromString(userId),
                caption,
                location,
                images
        ));
    }

    @GetMapping("/{postId}")
    @Operation(summary = "Get a post by ID")
    public ResponseEntity<PostDto> getPost(
            @PathVariable UUID postId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(value = "isLiked", required = false) Boolean isLiked) {
        UUID currentUserId = userId != null ? UUID.fromString(userId) : null;
        return ResponseEntity.ok(postService.getPost(postId, currentUserId, isLiked));
    }

    @PutMapping("/{postId}")
    @Operation(summary = "Update a post")
    public ResponseEntity<PostDto> updatePost(
            @PathVariable UUID postId,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, String> request) {
        return ResponseEntity.ok(postService.updatePost(
                postId,
                UUID.fromString(userId),
                request.get("caption"),
                request.get("location")
        ));
    }

    @DeleteMapping("/{postId}")
    @Operation(summary = "Delete a post")
    public ResponseEntity<Void> deletePost(
            @PathVariable UUID postId,
            @RequestHeader("X-User-Id") String userId) {
        postService.deletePost(postId, UUID.fromString(userId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get posts by user")
    public ResponseEntity<PagedResponse<PostDto>> getUserPosts(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId) {
        UUID currentId = currentUserId != null ? UUID.fromString(currentUserId) : null;
        return ResponseEntity.ok(postService.getUserPosts(userId, page, size, currentId));
    }

    @PostMapping("/feed")
    @Operation(summary = "Get feed posts by user IDs (internal use)")
    public ResponseEntity<PagedResponse<PostDto>> getFeedPosts(
            @RequestBody List<UUID> userIds,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(postService.getFeedPosts(userIds, page, size));
    }

    @GetMapping("/explore")
    @Operation(summary = "Get explore/popular posts")
    public ResponseEntity<PagedResponse<PostDto>> getExplorePosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(postService.getExplorePosts(page, size));
    }

    @PostMapping("/{postId}/likes/increment")
    @Operation(summary = "Increment post likes count (internal use)")
    public ResponseEntity<Void> incrementLikesCount(@PathVariable UUID postId) {
        postService.incrementLikesCount(postId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{postId}/likes/decrement")
    @Operation(summary = "Decrement post likes count (internal use)")
    public ResponseEntity<Void> decrementLikesCount(@PathVariable UUID postId) {
        postService.decrementLikesCount(postId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{postId}/comments/increment")
    @Operation(summary = "Increment post comments count (internal use)")
    public ResponseEntity<Void> incrementCommentsCount(@PathVariable UUID postId) {
        postService.incrementCommentsCount(postId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{postId}/comments/decrement")
    @Operation(summary = "Decrement post comments count (internal use)")
    public ResponseEntity<Void> decrementCommentsCount(@PathVariable UUID postId) {
        postService.decrementCommentsCount(postId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{postId}/owner")
    @Operation(summary = "Get post owner ID (internal use)")
    public ResponseEntity<UUID> getPostOwnerId(@PathVariable UUID postId) {
        Optional<UUID> ownerId = postService.getPostOwnerId(postId);
        return ownerId.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{postId}/exists")
    @Operation(summary = "Check if post exists (internal use)")
    public ResponseEntity<Boolean> postExists(@PathVariable UUID postId) {
        return ResponseEntity.ok(postService.postExists(postId));
    }
}
