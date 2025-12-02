package com.instagram.post.repository;

import com.instagram.post.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {

    @Query("SELECT p FROM Post p WHERE p.userId = :userId AND p.isActive = true ORDER BY p.createdAt DESC")
    Page<Post> findByUserIdAndIsActiveTrue(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.userId IN :userIds AND p.isActive = true ORDER BY p.createdAt DESC")
    Page<Post> findByUserIdInAndIsActiveTrue(@Param("userIds") List<UUID> userIds, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.isActive = true ORDER BY p.createdAt DESC")
    Page<Post> findAllActiveOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.isActive = true ORDER BY p.likesCount DESC, p.createdAt DESC")
    Page<Post> findPopularPosts(Pageable pageable);

    Optional<Post> findByIdAndIsActiveTrue(UUID id);

    long countByUserIdAndIsActiveTrue(UUID userId);

    @Modifying
    @Query("UPDATE Post p SET p.likesCount = p.likesCount + 1 WHERE p.id = :postId")
    void incrementLikesCount(@Param("postId") UUID postId);

    @Modifying
    @Query("UPDATE Post p SET p.likesCount = CASE WHEN p.likesCount > 0 THEN p.likesCount - 1 ELSE 0 END WHERE p.id = :postId")
    void decrementLikesCount(@Param("postId") UUID postId);

    @Modifying
    @Query("UPDATE Post p SET p.commentsCount = p.commentsCount + 1 WHERE p.id = :postId")
    void incrementCommentsCount(@Param("postId") UUID postId);

    @Modifying
    @Query("UPDATE Post p SET p.commentsCount = CASE WHEN p.commentsCount > 0 THEN p.commentsCount - 1 ELSE 0 END WHERE p.id = :postId")
    void decrementCommentsCount(@Param("postId") UUID postId);

    @Query("SELECT p.userId FROM Post p WHERE p.id = :postId")
    Optional<UUID> findUserIdByPostId(@Param("postId") UUID postId);
}
