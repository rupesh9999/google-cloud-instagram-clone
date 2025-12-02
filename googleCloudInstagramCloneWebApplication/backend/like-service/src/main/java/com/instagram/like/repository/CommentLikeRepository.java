package com.instagram.like.repository;

import com.instagram.like.entity.CommentLike;
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
public interface CommentLikeRepository extends JpaRepository<CommentLike, UUID> {

    Optional<CommentLike> findByCommentIdAndUserId(UUID commentId, UUID userId);

    boolean existsByCommentIdAndUserId(UUID commentId, UUID userId);

    void deleteByCommentIdAndUserId(UUID commentId, UUID userId);

    @Query("SELECT cl.userId FROM CommentLike cl WHERE cl.commentId = :commentId ORDER BY cl.createdAt DESC")
    Page<UUID> findUserIdsByCommentId(@Param("commentId") UUID commentId, Pageable pageable);

    long countByCommentId(UUID commentId);

    @Query("SELECT cl.commentId FROM CommentLike cl WHERE cl.commentId IN :commentIds AND cl.userId = :userId")
    List<UUID> findLikedCommentIds(@Param("commentIds") List<UUID> commentIds, @Param("userId") UUID userId);
}
