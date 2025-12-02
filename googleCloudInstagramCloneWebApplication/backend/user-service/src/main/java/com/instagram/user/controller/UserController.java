package com.instagram.user.controller;

import com.instagram.common.dto.PagedResponse;
import com.instagram.common.dto.UserDto;
import com.instagram.common.security.SecurityUtils;
import com.instagram.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profile and follow management")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @GetMapping("/{username}")
    @Operation(summary = "Get user profile by username")
    public ResponseEntity<UserDto> getUserProfile(
            @PathVariable String username,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId) {
        UUID userId = currentUserId != null ? UUID.fromString(currentUserId) : null;
        return ResponseEntity.ok(userService.getUserProfile(username, userId));
    }

    @GetMapping("/id/{userId}")
    @Operation(summary = "Get user by ID")
    public ResponseEntity<UserDto> getUserById(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @PostMapping
    @Operation(summary = "Create user profile (internal use)")
    public ResponseEntity<UserDto> createUser(@RequestBody Map<String, String> request) {
        UUID userId = UUID.fromString(request.get("userId"));
        String username = request.get("username");
        String email = request.get("email");
        String fullName = request.get("fullName");
        return ResponseEntity.ok(userService.createUser(userId, username, email, fullName));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update user profile")
    public ResponseEntity<UserDto> updateProfile(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, String> request) {
        String fullName = request.get("fullName");
        String bio = request.get("bio");
        return ResponseEntity.ok(userService.updateProfile(UUID.fromString(userId), fullName, bio));
    }

    @PostMapping(value = "/profile/picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Update profile picture")
    public ResponseEntity<UserDto> updateProfilePicture(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(userService.updateProfilePicture(UUID.fromString(userId), file));
    }

    @GetMapping("/search")
    @Operation(summary = "Search users")
    public ResponseEntity<PagedResponse<UserDto>> searchUsers(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId) {
        UUID userId = currentUserId != null ? UUID.fromString(currentUserId) : null;
        return ResponseEntity.ok(userService.searchUsers(query, page, size, userId));
    }

    @PostMapping("/{userId}/follow")
    @Operation(summary = "Follow a user")
    public ResponseEntity<Void> followUser(
            @PathVariable UUID userId,
            @RequestHeader("X-User-Id") String currentUserId) {
        userService.followUser(UUID.fromString(currentUserId), userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{userId}/follow")
    @Operation(summary = "Unfollow a user")
    public ResponseEntity<Void> unfollowUser(
            @PathVariable UUID userId,
            @RequestHeader("X-User-Id") String currentUserId) {
        userService.unfollowUser(UUID.fromString(currentUserId), userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{userId}/followers")
    @Operation(summary = "Get user's followers")
    public ResponseEntity<PagedResponse<UserDto>> getFollowers(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId) {
        UUID currentId = currentUserId != null ? UUID.fromString(currentUserId) : null;
        return ResponseEntity.ok(userService.getFollowers(userId, page, size, currentId));
    }

    @GetMapping("/{userId}/following")
    @Operation(summary = "Get users that a user is following")
    public ResponseEntity<PagedResponse<UserDto>> getFollowing(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId) {
        UUID currentId = currentUserId != null ? UUID.fromString(currentUserId) : null;
        return ResponseEntity.ok(userService.getFollowing(userId, page, size, currentId));
    }

    @GetMapping("/{userId}/following/ids")
    @Operation(summary = "Get IDs of users that a user is following (internal use)")
    public ResponseEntity<List<UUID>> getFollowingIds(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getFollowingIds(userId));
    }

    @GetMapping("/{followerId}/is-following/{followingId}")
    @Operation(summary = "Check if user is following another user")
    public ResponseEntity<Boolean> isFollowing(
            @PathVariable UUID followerId,
            @PathVariable UUID followingId) {
        return ResponseEntity.ok(userService.isFollowing(followerId, followingId));
    }

    @GetMapping("/suggestions")
    @Operation(summary = "Get suggested users to follow")
    public ResponseEntity<PagedResponse<UserDto>> getSuggestedUsers(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(userService.getSuggestedUsers(UUID.fromString(userId), page, size));
    }

    @PostMapping("/{userId}/posts/increment")
    @Operation(summary = "Increment user's post count (internal use)")
    public ResponseEntity<Void> incrementPostCount(@PathVariable UUID userId) {
        userService.incrementPostCount(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{userId}/posts/decrement")
    @Operation(summary = "Decrement user's post count (internal use)")
    public ResponseEntity<Void> decrementPostCount(@PathVariable UUID userId) {
        userService.decrementPostCount(userId);
        return ResponseEntity.ok().build();
    }
}
