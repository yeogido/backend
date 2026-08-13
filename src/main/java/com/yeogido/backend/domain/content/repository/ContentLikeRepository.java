package com.yeogido.backend.domain.content.repository;

import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.entity.ContentLike;
import com.yeogido.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContentLikeRepository extends JpaRepository<ContentLike,Long> {

    long countByContent(Content content);

    boolean existsByUserAndContent(User user, Content content);

    Optional<ContentLike> findByUserAndContent(User user, Content content);

    void deleteByContent(Content content);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        INSERT IGNORE INTO content_like (content_id, user_id, created_at, updated_at)
        VALUES (:contentId, :userId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """, nativeQuery = true)
    int insertIgnore(
            @Param("contentId") Long contentId,
            @Param("userId") Long userId
    );

    @Modifying(flushAutomatically = true)
    @Query("""
        DELETE FROM ContentLike contentLike
        WHERE contentLike.user.id = :userId
        """)
    int deleteAllByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT contentLike.content.id
        FROM ContentLike contentLike
        WHERE contentLike.user.id = :userId
          AND contentLike.content.id IN :contentIds
        """)
    List<Long> findLikedContentIds(
            @Param("userId") Long userId,
            @Param("contentIds") List<Long> contentIds
    );
}
