package com.yeogido.backend.domain.placeenrichment.repository;

import com.yeogido.backend.domain.placeenrichment.entity.PlaceOverviewApiOperation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceOverviewApiOperationRepository extends JpaRepository<PlaceOverviewApiOperation, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from PlaceOverviewApiOperation o where o.id = :id")
  Optional<PlaceOverviewApiOperation> findForUpdate(@Param("id") String id);
}
