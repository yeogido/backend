package com.yeogido.backend.domain.placeenrichment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeogido.backend.domain.content.client.TourApiClient;
import com.yeogido.backend.domain.content.exception.ContentErrorCode;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.placeenrichment.dto.TourRegionCode;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.exception.RegionErrorCode;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.global.exception.GeneralException;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Transactional
@RequiredArgsConstructor
public class TourRegionCodeMapper {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 10;
    private static final Map<String, String> PROVINCE_ALIASES = Map.ofEntries(
            Map.entry("서울", "서울특별시"), Map.entry("부산", "부산광역시"),
            Map.entry("대구", "대구광역시"), Map.entry("인천", "인천광역시"),
            Map.entry("광주", "광주광역시"), Map.entry("대전", "대전광역시"),
            Map.entry("울산", "울산광역시"), Map.entry("세종", "세종특별자치시"),
            Map.entry("경기", "경기도"), Map.entry("강원", "강원특별자치도"),
            Map.entry("강원도", "강원특별자치도"), Map.entry("충북", "충청북도"),
            Map.entry("충남", "충청남도"), Map.entry("전북", "전북특별자치도"),
            Map.entry("전라북도", "전북특별자치도"), Map.entry("전남", "전라남도"),
            Map.entry("경북", "경상북도"), Map.entry("경남", "경상남도"),
            Map.entry("제주", "제주특별자치도"), Map.entry("제주도", "제주특별자치도")
    );

    private final TourApiClient tourApiClient;
    private final RegionRepository regionRepository;
    // 최초 조회 후 재사용, 실패 및 불완전 목록 캐시 제외
    private List<CodeEntry> provinces;
    private final Map<String, List<CodeEntry>> districts = new HashMap<>();

    // DB 지역 코드 우선 사용 및 장소별 주소 보완
    public Optional<TourRegionCode> resolve(Place place) {
        return resolve(place.getRegion(), place.getRoadAddress(), place.getLotAddress());
    }

    public Optional<TourRegionCode> resolve(Region region, String roadAddress, String lotAddress) {
        Optional<TourRegionCode> mapped = resolveRegion(region);
        if (mapped.isPresent() && mapped.get().lDongSignguCd() != null) {
            return mapped;
        }
        if (mapped.isEmpty()) {
            return resolve(roadAddress, lotAddress);
        }
        // Region 시도 유지, 타 시도 주소 제외
        String provinceCode = mapped.get().lDongRegnCd();
        Optional<TourRegionCode> road = resolve(roadAddress)
                .filter(code -> code.lDongRegnCd().equals(provinceCode))
                .filter(code -> code.lDongSignguCd() != null);
        if (road.isPresent()) {
            return road;
        }
        return resolve(lotAddress)
                .filter(code -> code.lDongRegnCd().equals(provinceCode))
                .filter(code -> code.lDongSignguCd() != null)
                .or(() -> mapped);
    }

    private Optional<TourRegionCode> resolveRegion(Region region) {
        if (region == null) {
            return Optional.empty();
        }
        // 기존 Region의 최신 저장 코드 조회
        Region target = region.getId() == null ? region : regionRepository.findById(region.getId())
                .orElseThrow(() -> new GeneralException(RegionErrorCode.REGION_NOT_FOUND));
        if (StringUtils.hasText(target.getTourLdongRegnCd())) {
            return Optional.of(new TourRegionCode(
                    target.getTourLdongRegnCd(), target.getTourLdongSignguCd()));
        }
        // 부모 시도 기준 동명 시군구 구분
        List<String> names = new ArrayList<>();
        Region current = target;
        while (current != null) {
            names.addFirst(current.getName());
            current = current.getParent();
        }
        Optional<TourRegionCode> result = resolve(String.join(" ", names));
        if (result.isEmpty() && StringUtils.hasText(target.getFullName())) {
            result = resolve(target.getFullName());
        }
        // 주소 보완 전 Region 자체 매핑만 저장
        if (target.getId() != null) {
            result.ifPresent(code -> target.updateTourRegionCode(
                    code.lDongRegnCd(), code.lDongSignguCd()));
        }
        return result;
    }

    public Optional<TourRegionCode> resolve(String roadAddress, String lotAddress) {
        Optional<TourRegionCode> road = resolve(roadAddress);
        if (road.isPresent() && road.get().lDongSignguCd() != null) {
            return road;
        }
        Optional<TourRegionCode> lot = resolve(lotAddress);
        if (road.isEmpty()) {
            return lot;
        }
        return lot.filter(code -> code.lDongRegnCd().equals(road.get().lDongRegnCd()))
                .filter(code -> code.lDongSignguCd() != null)
                .or(() -> road);
    }

    public synchronized Optional<TourRegionCode> resolve(String address) {
        if (!StringUtils.hasText(address)) {
            return Optional.empty();
        }
        String[] tokens = address.strip().split("\\s+");
        if (provinces == null) {
            provinces = loadCodes(null);
        }
        List<CodeEntry> matched = provinces.stream()
                .filter(entry -> normalizeProvince(entry.name()).equals(normalizeProvince(tokens[0])))
                .toList();
        if (matched.size() != 1) {
            return Optional.empty();
        }
        String provinceCode = matched.getFirst().code();
        if (tokens.length < 2) {
            return Optional.of(new TourRegionCode(provinceCode, null));
        }
        List<CodeEntry> candidates = districts.computeIfAbsent(provinceCode, this::loadCodes);
        // 복합 시군구명 우선 매칭 (예: 수원시 영통구)
        for (int length = Math.min(2, tokens.length - 1); length >= 1; length--) {
            String districtName = length == 2 ? tokens[1] + tokens[2] : tokens[1];
            List<CodeEntry> matches = candidates.stream()
                    .filter(entry -> entry.name().replaceAll("\\s+", "").equals(districtName))
                    .toList();
            if (matches.size() == 1) {
                return Optional.of(new TourRegionCode(provinceCode, matches.getFirst().code()));
            }
        }
        return Optional.of(new TourRegionCode(provinceCode, null));
    }

    private List<CodeEntry> loadCodes(String provinceCode) {
        List<CodeEntry> result = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode response = tourApiClient.legalDistrictCodes(provinceCode, page, PAGE_SIZE);
            List<JsonNode> items = tourApiClient.items(response);
            for (JsonNode item : items) {
                String code = item.path("code").asText("");
                String name = item.path("name").asText("");
                if (!StringUtils.hasText(code) || !StringUtils.hasText(name)) {
                    throw new GeneralException(ContentErrorCode.TOUR_API_ERROR);
                }
                result.add(new CodeEntry(code, name));
            }
            int total = tourApiClient.totalCount(response);
            if (total > 0 && result.size() >= total) {
                return List.copyOf(result);
            }
            if (provinceCode != null && total == 0 && items.isEmpty() && page == 1) {
                return List.of();
            }
            if (items.isEmpty() || total == 0) {
                throw new GeneralException(ContentErrorCode.TOUR_API_ERROR);
            }
        }
        throw new GeneralException(ContentErrorCode.TOUR_API_ERROR);
    }

    private static String normalizeProvince(String name) {
        return PROVINCE_ALIASES.getOrDefault(name, name);
    }

    private record CodeEntry(String code, String name) {
    }
}
