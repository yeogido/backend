package com.yeogido.backend.domain.travel.repository;

import com.yeogido.backend.domain.travel.entity.Sticker;
import com.yeogido.backend.domain.travel.enums.StickerType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StickerRepository extends JpaRepository<Sticker, Long> {

    List<Sticker> findByIdIn(Collection<Long> stickerIds);

    List<Sticker> findAllByStickerTypeOrderByCategoryAscDisplayOrderAsc(
            StickerType stickerType
    );

    List<Sticker> findAllByUser_IdAndStickerTypeAndDeletedAtIsNullOrderByIdAsc(
            Long userId,
            StickerType stickerType
    );

    long countByUser_IdAndStickerTypeAndDeletedAtIsNull(
            Long userId,
            StickerType stickerType
    );

    long countByUser_IdAndStickerType(
            Long userId,
            StickerType stickerType
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            update Sticker sticker
            set sticker.deletedAt = :deletedAt
            where sticker.user.id = :userId
              and sticker.deletedAt is null
            """)
    int softDeleteAllByUserId(
            @Param("userId") Long userId,
            @Param("deletedAt") LocalDateTime deletedAt
    );
}
