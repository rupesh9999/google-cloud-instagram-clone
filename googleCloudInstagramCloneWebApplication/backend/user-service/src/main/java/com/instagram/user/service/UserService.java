package com.instagram.user.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.instagram.common.dto.PagedResponse;
import com.instagram.common.dto.UserDto;
import com.instagram.common.exception.BadRequestException;
import com.instagram.common.exception.ResourceNotFoundException;
import com.instagram.user.entity.Follow;
import com.instagram.user.entity.User;
import com.instagram.user.repository.FollowRepository;
import com.instagram.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final Storage storage;

    @Value("${gcs.bucket-name}")
    private String bucketName;

    @Transactional(readOnly = true)
    public UserDto getUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId.toString()));
        return mapToDto(user, null);
    }

    @Transactional(readOnly = true)
    public UserDto getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        return mapToDto(user, null);
    }

    @Transactional(readOnly = true)
    public UserDto getUserProfile(String username, UUID currentUserId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        
        boolean isFollowing = false;
        if (currentUserId != null && !user.getId().equals(currentUserId)) {
            isFollowing = followRepository.existsByFollowerIdAndFollowingId(currentUserId, user.getId());
        }
        
        return mapToDto(user, isFollowing);
    }

    public UserDto createUser(UUID userId, String username, String email, String fullName) {
        if (userRepository.existsByUsername(username)) {
            throw new BadRequestException("Username already taken");
        }
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email already registered");
        }

        User user = User.builder()
                .id(userId)
                .username(username)
                .email(email)
                .fullName(fullName)
                .build();

        user = userRepository.save(user);
        log.info("Created user profile for userId: {}", userId);
        return mapToDto(user, null);
    }

    public UserDto updateProfile(UUID userId, String fullName, String bio) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId.toString()));

        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName);
        }
        if (bio != null) {
            user.setBio(bio);
        }

        user = userRepository.save(user);
        log.info("Updated profile for userId: {}", userId);
        return mapToDto(user, null);
    }

    public UserDto updateProfilePicture(UUID userId, MultipartFile file) throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId.toString()));

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BadRequestException("File must be an image");
        }

        String fileName = String.format("profile-pictures/%s/%s", userId, UUID.randomUUID());
        String extension = getFileExtension(file.getOriginalFilename());
        if (extension != null) {
            fileName += extension;
        }

        BlobId blobId = BlobId.of(bucketName, fileName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(contentType)
                .build();

        storage.create(blobInfo, file.getBytes());

        String publicUrl = String.format("https://storage.googleapis.com/%s/%s", bucketName, fileName);
        user.setProfilePictureUrl(publicUrl);
        user = userRepository.save(user);

        log.info("Updated profile picture for userId: {}", userId);
        return mapToDto(user, null);
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserDto> searchUsers(String query, int page, int size, UUID currentUserId) {
        Pageable pageable = PageRequest.of(page, size);
        Page<User> usersPage = userRepository.searchUsers(query, pageable);

        List<UUID> userIds = usersPage.getContent().stream()
                .map(User::getId)
                .toList();

        Set<UUID> followingIds = currentUserId != null
                ? new HashSet<>(followRepository.findFollowingIdsAmong(currentUserId, userIds))
                : Collections.emptySet();

        List<UserDto> users = usersPage.getContent().stream()
                .map(user -> mapToDto(user, followingIds.contains(user.getId())))
                .toList();

        return PagedResponse.of(
                users,
                usersPage.getNumber(),
                usersPage.getSize(),
                usersPage.getTotalElements()
        );
    }

    public void followUser(UUID followerId, UUID followingId) {
        if (followerId.equals(followingId)) {
            throw new BadRequestException("Cannot follow yourself");
        }

        if (!userRepository.existsById(followingId)) {
            throw new ResourceNotFoundException("User", "id", followingId.toString());
        }

        if (followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw new BadRequestException("Already following this user");
        }

        Follow follow = Follow.builder()
                .followerId(followerId)
                .followingId(followingId)
                .build();

        followRepository.save(follow);
        log.info("User {} followed user {}", followerId, followingId);
    }

    public void unfollowUser(UUID followerId, UUID followingId) {
        if (followerId.equals(followingId)) {
            throw new BadRequestException("Cannot unfollow yourself");
        }

        Follow follow = followRepository.findByFollowerIdAndFollowingId(followerId, followingId)
                .orElseThrow(() -> new BadRequestException("Not following this user"));

        followRepository.delete(follow);
        log.info("User {} unfollowed user {}", followerId, followingId);
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserDto> getFollowers(UUID userId, int page, int size, UUID currentUserId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", "id", userId.toString());
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<UUID> followerIdsPage = followRepository.findFollowerIdsByFollowingId(userId, pageable);

        List<User> followers = userRepository.findByIdIn(followerIdsPage.getContent());

        Set<UUID> followingIds = currentUserId != null
                ? new HashSet<>(followRepository.findFollowingIdsAmong(currentUserId, followerIdsPage.getContent()))
                : Collections.emptySet();

        List<UserDto> users = followers.stream()
                .map(user -> mapToDto(user, followingIds.contains(user.getId())))
                .toList();

        return PagedResponse.of(
                users,
                followerIdsPage.getNumber(),
                followerIdsPage.getSize(),
                followerIdsPage.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserDto> getFollowing(UUID userId, int page, int size, UUID currentUserId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", "id", userId.toString());
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<UUID> followingIdsPage = followRepository.findFollowingIdsByFollowerId(userId, pageable);

        List<User> following = userRepository.findByIdIn(followingIdsPage.getContent());

        Set<UUID> currentUserFollowingIds = currentUserId != null
                ? new HashSet<>(followRepository.findFollowingIdsAmong(currentUserId, followingIdsPage.getContent()))
                : Collections.emptySet();

        List<UserDto> users = following.stream()
                .map(user -> mapToDto(user, currentUserFollowingIds.contains(user.getId())))
                .toList();

        return PagedResponse.of(
                users,
                followingIdsPage.getNumber(),
                followingIdsPage.getSize(),
                followingIdsPage.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public List<UUID> getFollowingIds(UUID userId) {
        return followRepository.findAllFollowingIdsByFollowerId(userId);
    }

    @Transactional(readOnly = true)
    public boolean isFollowing(UUID followerId, UUID followingId) {
        return followRepository.existsByFollowerIdAndFollowingId(followerId, followingId);
    }

    public void incrementPostCount(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId.toString()));
        user.setPostsCount(user.getPostsCount() + 1);
        userRepository.save(user);
    }

    public void decrementPostCount(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId.toString()));
        if (user.getPostsCount() > 0) {
            user.setPostsCount(user.getPostsCount() - 1);
            userRepository.save(user);
        }
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserDto> getSuggestedUsers(UUID userId, int page, int size) {
        List<UUID> followingIds = followRepository.findAllFollowingIdsByFollowerId(userId);
        followingIds.add(userId); // Exclude self

        Pageable pageable = PageRequest.of(page, size);
        Page<User> popularUsers = userRepository.findPopularUsers(pageable);

        List<UserDto> suggestions = popularUsers.getContent().stream()
                .filter(user -> !followingIds.contains(user.getId()))
                .map(user -> mapToDto(user, false))
                .limit(size)
                .toList();

        return PagedResponse.of(
                suggestions,
                page,
                size,
                (long) suggestions.size()
        );
    }

    private UserDto mapToDto(User user, Boolean isFollowing) {
        return UserDto.builder()
                .id(user.getId().toString())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .bio(user.getBio())
                .profilePictureUrl(user.getProfilePictureUrl())
                .followersCount(user.getFollowersCount())
                .followingCount(user.getFollowingCount())
                .postsCount(user.getPostsCount())
                .isFollowing(isFollowing)
                .createdAt(user.getCreatedAt())
                .build();
    }

    private String getFileExtension(String filename) {
        if (filename == null) return null;
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(lastDotIndex) : null;
    }
}
