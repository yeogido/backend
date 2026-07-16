package com.yeogido.backend.domain.place.repository;

import com.yeogido.backend.domain.place.entity.PlaceLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlaceLikeRepository extends JpaRepository<PlaceLike, Long> {

    // 특정 사용자가 해당 장소에 이미 좋아요를 등록했는지 확인
    boolean existsByUserIdAndPlaceId(Long userId, Long placeId);

    // 특정 사용자가 장소에 등록한 좋아요 데이터 단건 조회 (좋아요 취소에 사용)
    Optional<PlaceLike> findByUserIdAndPlaceId(Long userId, Long placeId);
}