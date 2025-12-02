package com.instagram.post.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.instagram.common.dto.PagedResponse;
import com.instagram.common.dto.PostDto;
import com.instagram.common.dto.UserDto;
import com.instagram.common.exception.BadRequestException;
import com.instagram.common.exception.ResourceNotFoundException;
import com.instagram.common.exception.UnauthorizedException;
import com.instagram.post.entity.Post;
import com.instagram.post.entity.PostImage;
import com.instagram.post.repository.PostRepository;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PostService {

    private final PostRepository postRepository;
    private final Storage storage;
    private final RestTemplate restTemplate;

    @Value("${gcs.bucket-name}")
    private String bucketName;

    @Value("${user.service.url}")
    private String userServiceUrl;

    public PostDto createPost(UUID userId, String caption, String location, List<MultipartFile> images) throws IOException {
        if (images == null || images.isEmpty()) {
            throw new BadRequestException("At least one image is required");
        }

        if (images.size() > 10) {
            throw new BadRequestException("Maximum 10 images allowed per post");
        }

        Post post = Post.builder()
                .userId(userId)
                .caption(caption)
                .location(location)
                .build();

        int order = 0;
        for (MultipartFile image : images) {
            String contentType = image.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new BadRequestException("All files must be images");
            }

            String imageUrl = uploadImage(userId, image);
            PostImage postImage = PostImage.builder()
                    .imageUrl(imageUrl)
                    .displayOrder(order++)
                    .build();
            post.addImage(postImage);
        }

        post = postRepository.save(post);

        // Notify user-service to increment post count
        try {
            restTemplate.postForEntity(
                    userServiceUrl + "/api/users/" + userId + "/posts/increment",
                    null,
                    Void.class
            );
        } catch (Exception e) {
            log.warn("Failed to increment post count for user {}: {}", userId, e.getMessage());
        }

        log.info("Created post {} for user {}", post.getId(), userId);
        return mapToDto(post, null, null, null);
    }

    @Transactional(readOnly = true)
    public PostDto getPost(UUID postId, UUID currentUserId, Boolean isLiked) {
        Post post = postRepository.findByIdAndIsActiveTrue(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId.toString()));

        UserDto user = fetchUser(post.getUserId());
        return mapToDto(post, user, isLiked, null);
    }

    public PostDto updatePost(UUID postId, UUID userId, String caption, String location) {
        Post post = postRepository.findByIdAndIsActiveTrue(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId.toString()));

        if (!post.getUserId().equals(userId)) {
            throw new UnauthorizedException("You can only update your own posts");
        }

        if (caption != null) {
            post.setCaption(caption);
        }
        if (location != null) {
            post.setLocation(location);
        }

        post = postRepository.save(post);
        log.info("Updated post {}", postId);
        return mapToDto(post, null, null, null);
    }

    public void deletePost(UUID postId, UUID userId) {
        Post post = postRepository.findByIdAndIsActiveTrue(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId.toString()));

        if (!post.getUserId().equals(userId)) {
            throw new UnauthorizedException("You can only delete your own posts");
        }

        post.setIsActive(false);
        postRepository.save(post);

        // Notify user-service to decrement post count
        try {
            restTemplate.postForEntity(
                    userServiceUrl + "/api/users/" + userId + "/posts/decrement",
                    null,
                    Void.class
            );
        } catch (Exception e) {
            log.warn("Failed to decrement post count for user {}: {}", userId, e.getMessage());
        }

        log.info("Deleted post {}", postId);
    }

    @Transactional(readOnly = true)
    public PagedResponse<PostDto> getUserPosts(UUID userId, int page, int size, UUID currentUserId) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Post> postsPage = postRepository.findByUserIdAndIsActiveTrue(userId, pageable);

        UserDto user = fetchUser(userId);
        List<PostDto> posts = postsPage.getContent().stream()
                .map(post -> mapToDto(post, user, null, null))
                .toList();

        return PagedResponse.of(
                posts,
                postsPage.getNumber(),
                postsPage.getSize(),
                postsPage.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<PostDto> getFeedPosts(List<UUID> userIds, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Post> postsPage = postRepository.findByUserIdInAndIsActiveTrue(userIds, pageable);

        // Fetch all users at once
        Set<UUID> postUserIds = postsPage.getContent().stream()
                .map(Post::getUserId)
                .collect(Collectors.toSet());
        Map<UUID, UserDto> usersMap = fetchUsers(postUserIds);

        List<PostDto> posts = postsPage.getContent().stream()
                .map(post -> mapToDto(post, usersMap.get(post.getUserId()), null, null))
                .toList();

        return PagedResponse.of(
                posts,
                postsPage.getNumber(),
                postsPage.getSize(),
                postsPage.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<PostDto> getExplorePosts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Post> postsPage = postRepository.findPopularPosts(pageable);

        Set<UUID> userIds = postsPage.getContent().stream()
                .map(Post::getUserId)
                .collect(Collectors.toSet());
        Map<UUID, UserDto> usersMap = fetchUsers(userIds);

        List<PostDto> posts = postsPage.getContent().stream()
                .map(post -> mapToDto(post, usersMap.get(post.getUserId()), null, null))
                .toList();

        return PagedResponse.of(
                posts,
                postsPage.getNumber(),
                postsPage.getSize(),
                postsPage.getTotalElements()
        );
    }

    public void incrementLikesCount(UUID postId) {
        postRepository.incrementLikesCount(postId);
    }

    public void decrementLikesCount(UUID postId) {
        postRepository.decrementLikesCount(postId);
    }

    public void incrementCommentsCount(UUID postId) {
        postRepository.incrementCommentsCount(postId);
    }

    public void decrementCommentsCount(UUID postId) {
        postRepository.decrementCommentsCount(postId);
    }

    @Transactional(readOnly = true)
    public Optional<UUID> getPostOwnerId(UUID postId) {
        return postRepository.findUserIdByPostId(postId);
    }

    @Transactional(readOnly = true)
    public boolean postExists(UUID postId) {
        return postRepository.findByIdAndIsActiveTrue(postId).isPresent();
    }

    private String uploadImage(UUID userId, MultipartFile image) throws IOException {
        String fileName = String.format("posts/%s/%s", userId, UUID.randomUUID());
        String extension = getFileExtension(image.getOriginalFilename());
        if (extension != null) {
            fileName += extension;
        }

        BlobId blobId = BlobId.of(bucketName, fileName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(image.getContentType())
                .build();

        storage.create(blobInfo, image.getBytes());

        return String.format("https://storage.googleapis.com/%s/%s", bucketName, fileName);
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

    private PostDto mapToDto(Post post, UserDto user, Boolean isLiked, Boolean isSaved) {
        List<String> imageUrls = post.getImages().stream()
                .sorted(Comparator.comparingInt(PostImage::getDisplayOrder))
                .map(PostImage::getImageUrl)
                .toList();

        return PostDto.builder()
                .id(post.getId().toString())
                .userId(post.getUserId().toString())
                .user(user)
                .caption(post.getCaption())
                .imageUrls(imageUrls)
                .location(post.getLocation())
                .likesCount(post.getLikesCount())
                .commentsCount(post.getCommentsCount())
                .isLiked(isLiked)
                .isSaved(isSaved)
                .createdAt(post.getCreatedAt())
                .build();
    }

    private String getFileExtension(String filename) {
        if (filename == null) return null;
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(lastDotIndex) : null;
    }
}
