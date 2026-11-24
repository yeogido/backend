package com.yeogido.backend.domain.travel.repository;

import com.yeogido.backend.domain.travel.entity.TravelRecordPhoto;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelRecordPhotoRepository extends JpaRepository<TravelRecordPhoto, Long> {

    @EntityGraph(attributePaths = "uploader")
    List<TravelRecordPhoto> findByTravelRecordIdOrderByImageOrderAsc(Long travelRecordId);

    @EntityGraph(attributePaths = "uploader")
    List<TravelRecordPhoto> findByTravelRecordIdInOrderByTravelRecordIdAscImageOrderAsc(List<Long> recordIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select photo from TravelRecordPhoto photo where photo.travelRecord.id = :recordId order by photo.imageOrder")
    List<TravelRecordPhoto> findCurrentPhotosForUpdate(@Param("recordId") Long recordId);

    @Query("select distinct photo.travelRecord.id from TravelRecordPhoto photo where photo.uploader.id = :userId")
    List<Long> findUploadedRecordIds(@Param("userId") Long userId);

    void deleteAllByTravelRecord_Id(Long travelRecordId);

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from TravelRecordPhoto photo
            where photo.travelRecord.user.id = :userId
            """)
    int deleteAllByTravelRecordUserId(@Param("userId") Long userId);
}
