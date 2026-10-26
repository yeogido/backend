package com.yeogido.backend.domain.place.enrichment;

// 시군구 미확정 시 lDongSignguCd null
public record TourRegionCode(String lDongRegnCd, String lDongSignguCd) {
}
