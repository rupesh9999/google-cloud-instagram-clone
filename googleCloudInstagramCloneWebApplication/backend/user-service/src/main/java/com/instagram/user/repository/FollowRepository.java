package com.instagram.user.repository;

import com.instagram.user.entity.Follow;
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
public interface FollowRepository extends JpaRepository<Follow, UUID> {

    Optional<Follow> findByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

    boolean existsByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

    @Query("SELECT f.followingId FROM Follow f WHERE f.followerId = :userId")
    Page<UUID> findFollowingIdsByFollowerId(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT f.followerId FROM Follow f WHERE f.followingId = :userId")
    Page<UUID> findFollowerIdsByFollowingId(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT f.followingId FROM Follow f WHERE f.followerId = :userId")
    List<UUID> findAllFollowingIdsByFollowerId(@Param("userId") UUID userId);

    @Query("SELECT f.followerId FROM Follow f WHERE f.followingId = :userId")
    List<UUID> findAllFollowerIdsByFollowingId(@Param("userId") UUID userId);

    long countByFollowerId(UUID followerId);

    long countByFollowingId(UUID followingId);

    void deleteByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

    @Query("SELECT f.followingId FROM Follow f WHERE f.followerId = :userId AND f.followingId IN :userIds")
    List<UUID> findFollowingIdsAmong(@Param("userId") UUID userId, @Param("userIds") List<UUID> userIds);
}
