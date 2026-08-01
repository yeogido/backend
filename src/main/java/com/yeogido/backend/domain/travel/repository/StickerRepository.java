package com.yeogido.backend.domain.travel.repository;

import com.yeogido.backend.domain.travel.entity.Sticker;
import com.yeogido.backend.domain.travel.enums.StickerType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StickerRepository extends JpaRepository<Sticker, Long> {

    List<Sticker> findByIdIn(Collection<Long> stickerIds);

    List<Sticker> findAllByStickerTypeOrderByCategoryAscDisplayOrderAsc(
            StickerType stickerType
    );

    List<Sticker> findAllByUser_IdAndStickerTypeAndDeletedAtIsNullOrderByIdAsc(
            Long userId,
            StickerType stickerType
    );
}
