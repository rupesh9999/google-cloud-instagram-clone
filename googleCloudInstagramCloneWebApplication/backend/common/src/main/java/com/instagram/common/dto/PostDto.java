package com.instagram.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostDto {
    private String id;
    private String userId;
    private String username;
    private String userProfilePicture;
    private UserDto user;
    private String imageUrl;
    private List<String> imageUrls;
    private String caption;
    private String location;
    private List<String> hashtags;
    private Long likesCount;
    private Long commentsCount;
    private Boolean isLiked;
    private Boolean isSaved;
    private LocalDateTime createdAt;
}
