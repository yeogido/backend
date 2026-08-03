package com.yeogido.backend.domain.travel.repository;

import com.yeogido.backend.domain.travel.entity.TravelRecordPhoto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelRecordPhotoRepository extends JpaRepository<TravelRecordPhoto, Long> {

    List<TravelRecordPhoto> findByTravelRecordIdOrderByImageOrderAsc(Long travelRecordId);

    void deleteAllByTravelRecord_Id(Long travelRecordId);

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from TravelRecordPhoto photo
            where photo.travelRecord.user.id = :userId
            """)
    int deleteAllByTravelRecordUserId(@Param("userId") Long userId);
}
