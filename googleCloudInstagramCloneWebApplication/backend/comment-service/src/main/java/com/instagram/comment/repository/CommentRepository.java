package com.instagram.comment.repository;

import com.instagram.comment.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {

    @Query("SELECT c FROM Comment c WHERE c.postId = :postId AND c.parentId IS NULL AND c.isActive = true ORDER BY c.createdAt DESC")
    Page<Comment> findTopLevelCommentsByPostId(@Param("postId") UUID postId, Pageable pageable);

    @Query("SELECT c FROM Comment c WHERE c.parentId = :parentId AND c.isActive = true ORDER BY c.createdAt ASC")
    Page<Comment> findRepliesByParentId(@Param("parentId") UUID parentId, Pageable pageable);

    Optional<Comment> findByIdAndIsActiveTrue(UUID id);

    long countByPostIdAndIsActiveTrue(UUID postId);

    @Modifying
    @Query("UPDATE Comment c SET c.likesCount = c.likesCount + 1 WHERE c.id = :commentId")
    void incrementLikesCount(@Param("commentId") UUID commentId);

    @Modifying
    @Query("UPDATE Comment c SET c.likesCount = CASE WHEN c.likesCount > 0 THEN c.likesCount - 1 ELSE 0 END WHERE c.id = :commentId")
    void decrementLikesCount(@Param("commentId") UUID commentId);

    @Query("SELECT c.userId FROM Comment c WHERE c.id = :commentId")
    Optional<UUID> findUserIdByCommentId(@Param("commentId") UUID commentId);

    @Query("SELECT c.postId FROM Comment c WHERE c.id = :commentId")
    Optional<UUID> findPostIdByCommentId(@Param("commentId") UUID commentId);
}
