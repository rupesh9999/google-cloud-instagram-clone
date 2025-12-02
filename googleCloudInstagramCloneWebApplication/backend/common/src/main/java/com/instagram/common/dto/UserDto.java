package com.instagram.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private String id;
    private String username;
    private String email;
    private String fullName;
    private String bio;
    private String profilePictureUrl;
    private Long followersCount;
    private Long followingCount;
    private Long postsCount;
    private Boolean isFollowing;
    private LocalDateTime createdAt;
}
