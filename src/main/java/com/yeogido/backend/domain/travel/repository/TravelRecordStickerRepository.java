package com.yeogido.backend.domain.travel.repository;

import com.yeogido.backend.domain.travel.entity.TravelRecordSticker;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelRecordStickerRepository extends JpaRepository<TravelRecordSticker, Long> {

    @Query("""
            select trs
            from TravelRecordSticker trs
            join fetch trs.sticker
            where trs.travelRecord.id = :travelRecordId
            order by trs.zIndex asc
            """)
    List<TravelRecordSticker> findByTravelRecordIdOrderByZIndexAsc(
            @Param("travelRecordId") Long travelRecordId
    );
}
