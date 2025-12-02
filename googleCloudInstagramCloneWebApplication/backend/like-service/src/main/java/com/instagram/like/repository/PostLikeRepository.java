package com.instagram.like.repository;

import com.instagram.like.entity.PostLike;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, UUID> {

    Optional<PostLike> findByPostIdAndUserId(UUID postId, UUID userId);

    boolean existsByPostIdAndUserId(UUID postId, UUID userId);

    void deleteByPostIdAndUserId(UUID postId, UUID userId);

    @Query("SELECT pl.userId FROM PostLike pl WHERE pl.postId = :postId ORDER BY pl.createdAt DESC")
    Page<UUID> findUserIdsByPostId(@Param("postId") UUID postId, Pageable pageable);

    long countByPostId(UUID postId);

    @Query("SELECT pl.postId FROM PostLike pl WHERE pl.postId IN :postIds AND pl.userId = :userId")
    List<UUID> findLikedPostIds(@Param("postIds") List<UUID> postIds, @Param("userId") UUID userId);
}
