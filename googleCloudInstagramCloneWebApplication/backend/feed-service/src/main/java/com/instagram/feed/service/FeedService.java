package com.instagram.feed.service;

import com.instagram.common.dto.PagedResponse;
import com.instagram.common.dto.PostDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedService {

    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${user.service.url}")
    private String userServiceUrl;

    @Value("${post.service.url}")
    private String postServiceUrl;

    @Value("${like.service.url}")
    private String likeServiceUrl;

    @Value("${feed.cache.ttl:300}")
    private long cacheTtlSeconds;

    private static final String FEED_CACHE_KEY = "feed:user:";

    @SuppressWarnings("unchecked")
    public PagedResponse<PostDto> getFeed(UUID userId, int page, int size) {
        String cacheKey = FEED_CACHE_KEY + userId + ":" + page + ":" + size;

        // Try cache first
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.debug("Feed cache hit for user {}", userId);
                return (PagedResponse<PostDto>) cached;
            }
        } catch (Exception e) {
            log.warn("Redis cache read failed: {}", e.getMessage());
        }

        // Get following IDs
        List<UUID> followingIds = getFollowingIds(userId);
        
        // Include user's own posts
        followingIds = new ArrayList<>(followingIds);
        followingIds.add(userId);

        // Fetch posts from post-service
        PagedResponse<PostDto> feed = fetchFeedPosts(followingIds, page, size);

        // Enrich posts with like status
        if (feed != null && feed.getContent() != null && !feed.getContent().isEmpty()) {
            enrichPostsWithLikeStatus(feed.getContent(), userId);
        }

        // Cache the result
        try {
            if (feed != null) {
                redisTemplate.opsForValue().set(cacheKey, feed, cacheTtlSeconds, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("Redis cache write failed: {}", e.getMessage());
        }

        return feed;
    }

    public void invalidateFeedCache(UUID userId) {
        try {
            Set<String> keys = redisTemplate.keys(FEED_CACHE_KEY + userId + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Invalidated feed cache for user {}", userId);
            }
        } catch (Exception e) {
            log.warn("Failed to invalidate feed cache: {}", e.getMessage());
        }
    }

    public void invalidateFeedCacheForFollowers(UUID userId) {
        // Get all followers
        List<UUID> followerIds = getFollowerIds(userId);
        for (UUID followerId : followerIds) {
            invalidateFeedCache(followerId);
        }
    }

    @SuppressWarnings("unchecked")
    private List<UUID> getFollowingIds(UUID userId) {
        try {
            String url = userServiceUrl + "/api/users/" + userId + "/following/ids";
            ResponseEntity<List<UUID>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<UUID>>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to get following IDs for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private List<UUID> getFollowerIds(UUID userId) {
        try {
            String url = userServiceUrl + "/api/users/" + userId + "/followers/ids";
            ResponseEntity<List<UUID>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<UUID>>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to get follower IDs for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private PagedResponse<PostDto> fetchFeedPosts(List<UUID> userIds, int page, int size) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(postServiceUrl)
                    .path("/api/posts/feed")
                    .queryParam("page", page)
                    .queryParam("size", size)
                    .toUriString();

            ResponseEntity<PagedResponse<PostDto>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(userIds),
                    new ParameterizedTypeReference<PagedResponse<PostDto>>() {}
            );

            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to fetch feed posts: {}", e.getMessage());
            return PagedResponse.of(Collections.emptyList(), page, size, 0L);
        }
    }

    private void enrichPostsWithLikeStatus(List<PostDto> posts, UUID userId) {
        try {
            List<String> postIds = posts.stream()
                    .map(PostDto::getId)
                    .toList();

            String url = likeServiceUrl + "/api/likes/posts/status";
            ResponseEntity<Map<String, Boolean>> response = restTemplate.exchange(
                    url + "?userId=" + userId,
                    HttpMethod.POST,
                    new HttpEntity<>(postIds),
                    new ParameterizedTypeReference<Map<String, Boolean>>() {}
            );

            Map<String, Boolean> likeStatusMap = response.getBody();
            if (likeStatusMap != null) {
                for (PostDto post : posts) {
                    post.setIsLiked(likeStatusMap.getOrDefault(post.getId(), false));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to enrich posts with like status: {}", e.getMessage());
        }
    }
}
