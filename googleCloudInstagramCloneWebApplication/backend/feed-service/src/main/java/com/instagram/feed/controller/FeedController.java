package com.instagram.feed.controller;

import com.instagram.common.dto.PagedResponse;
import com.instagram.common.dto.PostDto;
import com.instagram.feed.service.FeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
@Tag(name = "Feed", description = "Feed aggregation endpoints")
@SecurityRequirement(name = "bearerAuth")
public class FeedController {

    private final FeedService feedService;

    @GetMapping
    @Operation(summary = "Get user's feed")
    public ResponseEntity<PagedResponse<PostDto>> getFeed(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(feedService.getFeed(UUID.fromString(userId), page, size));
    }

    @PostMapping("/invalidate")
    @Operation(summary = "Invalidate feed cache for a user (internal use)")
    public ResponseEntity<Void> invalidateFeedCache(
            @RequestParam UUID userId) {
        feedService.invalidateFeedCache(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/invalidate/followers")
    @Operation(summary = "Invalidate feed cache for all followers of a user (internal use)")
    public ResponseEntity<Void> invalidateFeedCacheForFollowers(
            @RequestParam UUID userId) {
        feedService.invalidateFeedCacheForFollowers(userId);
        return ResponseEntity.ok().build();
    }
}
