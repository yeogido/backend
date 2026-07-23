package com.yeogido.backend.domain.place.repository;

import com.yeogido.backend.domain.place.entity.PlaceLike;
import com.yeogido.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PlaceLikeRepository extends JpaRepository<PlaceLike, Long> {

    // 멱등한 좋아요 등록을 위해 기존 좋아요 존재 여부 확인
    boolean existsByUserIdAndPlaceId(Long userId, Long placeId);

    // 좋아요가 존재하는 경우에만 삭제하기 위한 단건 조회
    Optional<PlaceLike> findByUserIdAndPlaceId(Long userId, Long placeId);
}