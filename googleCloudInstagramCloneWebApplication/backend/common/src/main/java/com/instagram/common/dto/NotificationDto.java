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
public class NotificationDto {
    private String id;
    private NotificationType type;
    private String fromUserId;
    private String fromUsername;
    private String fromUserProfilePicture;
    private String postId;
    private String postImageUrl;
    private String message;
    private Boolean isRead;
    private LocalDateTime createdAt;

    public enum NotificationType {
        LIKE, COMMENT, FOLLOW
    }
}
