package com.yeogido.backend.domain.place.repository;

import com.yeogido.backend.domain.place.entity.PlaceLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlaceLikeRepository extends JpaRepository<PlaceLike, Long> {

    // 멱등한 좋아요 등록을 위해 기존 좋아요 존재 여부 확인
    boolean existsByUserIdAndPlaceId(Long userId, Long placeId);

    // 특정 장소의 전체 좋아요 수 조회
    long countByPlaceId(Long placeId);

    // 좋아요가 존재하는 경우에만 삭제하기 위한 단건 조회
    Optional<PlaceLike> findByUserIdAndPlaceId(Long userId, Long placeId);
}