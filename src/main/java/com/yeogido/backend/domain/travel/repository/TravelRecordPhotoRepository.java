package com.yeogido.backend.domain.travel.repository;

import com.yeogido.backend.domain.travel.entity.TravelRecordPhoto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelRecordPhotoRepository extends JpaRepository<TravelRecordPhoto, Long> {

    List<TravelRecordPhoto> findByTravelRecordIdOrderByImageOrderAsc(Long travelRecordId);

    void deleteAllByTravelRecord_Id(Long travelRecordId);
}
