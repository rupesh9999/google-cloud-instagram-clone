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
    private String imageUrl;
    private String caption;
    private List<String> hashtags;
    private Long likesCount;
    private Long commentsCount;
    private Boolean isLiked;
    private LocalDateTime createdAt;
}
