package com.yeogido.backend.domain.placeenrichment.service;

public interface PlaceOverviewEnrichmentService {

  // 장소 생성 트랜잭션 커밋 이후 호출을 전제로 한 오버뷰 조회 및 저장
  void enrich(Long placeId);
}
