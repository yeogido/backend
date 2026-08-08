package com.yeogido.backend.domain.place.repository;

import com.yeogido.backend.domain.place.entity.PlaceOperatingDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PlaceOperatingDayRepository extends JpaRepository<PlaceOperatingDay, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PlaceOperatingDay pod where pod.place.id = :placeId")
    void deleteAllByPlace_Id(Long placeId);
}
