package com.yeogido.backend.domain.placeenrichment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeogido.backend.domain.content.client.TourApiClient;
import com.yeogido.backend.domain.content.exception.ContentErrorCode;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.placeenrichment.dto.TourRegionCode;
import com.yeogido.backend.domain.placeenrichment.dto.response.TourPlaceResponse;
import com.yeogido.backend.domain.placeenrichment.enums.TourPlaceMatchReason;
import com.yeogido.backend.domain.placeenrichment.enums.TourPlaceMatchStatus;
import com.yeogido.backend.domain.placeenrichment.exception.PlaceEnrichmentErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.yeogido.backend.domain.placeenrichment.enums.TourPlaceMatchReason.*;
import static com.yeogido.backend.domain.placeenrichment.enums.TourPlaceMatchStatus.*;

@Service
@RequiredArgsConstructor
public class TourPlaceEnrichmentServiceImpl implements TourPlaceEnrichmentService {

    private static final int PAGE_SIZE = 20;
    private static final Pattern BUILDING_ADDRESS = Pattern.compile("^(.+?(?:로|길|동|읍|면|리))\\s+(\\d+(?:-\\d+)?)(?=\\s|$|[,(])");
    private final TourApiClient tourApiClient;
    private final TourRegionCodeMapper regionCodeMapper;

    @Value("${app.tour-api.place-max-search-requests:12}")
    private int maxSearchRequests;

    @Value("${app.tour-api.place-match-distance-meters:100}")
    private double matchDistanceMeters;

    @Override
    public TourPlaceResponse.MatchResult enrich(Place place) {
        // 장소명 및 검색 설정 검증
        if (!StringUtils.hasText(place.getName())) {
            throw new GeneralException(PlaceEnrichmentErrorCode.PLACE_NAME_REQUIRED);
        }
        if (maxSearchRequests < 1 || matchDistanceMeters <= 0 || !Double.isFinite(matchDistanceMeters)) {
            throw new GeneralException(PlaceEnrichmentErrorCode.INVALID_TOUR_PLACE_SEARCH_CONFIG);
        }
        // Region 및 주소 기반 지역 코드 결정 및 검색어 구성
        TourRegionCode region = regionCodeMapper.resolve(place).orElse(new TourRegionCode(null, null));
        List<String> keywords = keywords(place.getName());
        // 시도, 시군구 우선 검색 후 시도 단위로 범위 확장
        List<TourRegionCode> scopes = new ArrayList<>();
        scopes.add(region);
        if (region.lDongSignguCd() != null) {
            scopes.add(new TourRegionCode(region.lDongRegnCd(), null));
        }
        Map<String, TourPlaceResponse.Candidate> candidates = new LinkedHashMap<>();
        int requests = 0;
        for (TourRegionCode scope : scopes) {
            for (String keyword : keywords) {
                int loaded = 0;
                for (int page = 1; ; page++) {
                    if (requests >= maxSearchRequests) {
                        return result(place, AMBIGUOUS, SEARCH_LIMIT_REACHED, null,
                                null, requests, true, candidates);
                    }
                    JsonNode response = tourApiClient.searchPlaces(keyword, scope.lDongRegnCd(),
                            scope.lDongSignguCd(), page, PAGE_SIZE);
                    requests++;
                    List<JsonNode> items = tourApiClient.items(response);
                    int total = tourApiClient.totalCount(response);
                    // 콘텐츠 ID 기준 후보 중복 제거 및 일치 근거 수집
                    for (JsonNode item : items) {
                        String id = item.path("contentid").asText("");
                        if (StringUtils.hasText(id)) {
                            candidates.put(id, evaluate(place, item));
                        }
                    }
                    // 현재 검색 조건의 전체 페이지 확인
                    loaded += items.size();
                    if (loaded >= total) {
                        break;
                    }
                    // 전체 건수와 빈 페이지 응답의 불일치 감지
                    if (items.isEmpty()) {
                        throw new GeneralException(ContentErrorCode.TOUR_API_ERROR);
                    }
                }
                // 누적 후보 중 이름과 위치가 모두 일치하는 확정 후보 선별
                List<TourPlaceResponse.Candidate> strong = candidates.values().stream()
                        .filter(TourPlaceResponse.Candidate::strongMatch).toList();
                // 복수 확정 후보에 대한 자동 매칭 보류
                if (strong.size() > 1) {
                    return result(place, AMBIGUOUS, MULTIPLE_MATCHES, null,
                            null, requests, false, candidates);
                }
                if (strong.size() == 1) {
                    // 단일 확정 후보의 상세 조회 및 콘텐츠 ID 검증
                    String contentId = strong.getFirst().contentId();
                    List<JsonNode> details = tourApiClient.items(tourApiClient.detailCommon(contentId));
                    JsonNode detail = details.stream()
                            .filter(item -> contentId.equals(item.path("contentid").asText()))
                            .findFirst().orElseThrow(() -> new GeneralException(ContentErrorCode.TOUR_API_ERROR));
                    String overview = plainText(detail.path("overview").asText(""));
                    return result(place, MATCHED, overview == null ? MATCHED_WITHOUT_OVERVIEW : NAME_AND_LOCATION_MATCHED,
                            contentId, overview, requests, false, candidates);
                }
            }
        }
        // 일부 근거만 일치하는 후보와 일치 근거가 없는 결과의 구분
        boolean plausible = candidates.values().stream().anyMatch(candidate -> candidate.nameMatched()
                || candidate.addressMatched()
                || candidate.distanceMeters() != null && candidate.distanceMeters() <= matchDistanceMeters);
        return result(place, plausible ? AMBIGUOUS : UNMATCHED,
                plausible ? INSUFFICIENT_EVIDENCE : NO_MATCH,
                null, null, requests, false, candidates);
    }

    private TourPlaceResponse.Candidate evaluate(Place place, JsonNode item) {
        String name = item.path("title").asText("");
        String address = item.path("addr1").asText("");
        String original = normalizeName(place.getName());
        String candidateName = normalizeName(name);
        // 원본명 또는 입력 지점명 제거 결과와 후보명 비교
        boolean nameMatched = !candidateName.isEmpty() && (original.equals(candidateName)
                || normalizeName(withoutBranch(place.getName())).equals(candidateName));
        // 주소 일치 또는 기준 거리 이내 좌표를 위치 근거로 사용
        boolean addressMatched = sameAddress(place.getRoadAddress(), address)
                || sameAddress(place.getLotAddress(), address);
        Double distance = distance(place, item);
        boolean locationMatched = addressMatched || distance != null && distance <= matchDistanceMeters;
        // 원거리 좌표와 주소 일치가 충돌하는 후보의 확정 제외
        boolean conflict = distance != null && distance > Math.max(500, matchDistanceMeters * 3);
        return new TourPlaceResponse.Candidate(item.path("contentid").asText(), name, address,
                distance, nameMatched, addressMatched, nameMatched && locationMatched && !conflict);
    }

    // 원본 → 특수문자, 공백 정리 → 공백 제거 → 지점명 제거 순서의 검색어 구성
    private static List<String> keywords(String name) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.add(name.strip());
        result.add(name.replaceAll("[^\\p{L}\\p{N}\\s]", " ").strip().replaceAll("\\s+", " "));
        result.add(normalizeName(name));
        result.add(withoutBranch(name));
        return result.stream().filter(StringUtils::hasText).toList();
    }

    // 공백으로 분리된 마지막 본점, 지점명 제거
    private static String withoutBranch(String name) {
        return name.strip().replaceFirst("\\s+(?:본점|[^\\s]+점)$", "");
    }

    // 이름 비교용 소문자 변환 및 문자, 숫자 외 기호 제거
    private static String normalizeName(String name) {
        return name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static boolean sameAddress(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        return addressKey(left).equals(addressKey(right));
    }

    // 시도 표기 통일 및 건물 번호 기준 주소 비교 키 생성
    private static String addressKey(String address) {
        String normalized = address.strip().replaceAll("\\s+", " ")
                .replace("서울특별시", "서울").replace("부산광역시", "부산")
                .replace("대구광역시", "대구").replace("인천광역시", "인천")
                .replace("광주광역시", "광주").replace("대전광역시", "대전")
                .replace("울산광역시", "울산").replace("세종특별자치시", "세종")
                .replace("경기도", "경기").replace("강원특별자치도", "강원").replace("강원도", "강원")
                .replace("충청북도", "충북").replace("충청남도", "충남")
                .replace("전북특별자치도", "전북").replace("전라북도", "전북").replace("전라남도", "전남")
                .replace("경상북도", "경북").replace("경상남도", "경남")
                .replace("제주특별자치도", "제주").replace("제주도", "제주");
        // 건물 번호 보존 및 층·괄호 등 부가 주소 제외
        Matcher matcher = BUILDING_ADDRESS.matcher(normalized);
        return (matcher.find() ? matcher.group(1) + matcher.group(2) : normalized).replaceAll("\\s+", "");
    }

    // 두 좌표 간 하버사인 거리 계산, 좌표 누락 및 오류 시 null 반환
    private static Double distance(Place place, JsonNode item) {
        if (place.getLatitude() == null || place.getLongitude() == null) {
            return null;
        }
        try {
            double lat = Double.parseDouble(item.path("mapy").asText());
            double lon = Double.parseDouble(item.path("mapx").asText());
            double sourceLat = place.getLatitude().doubleValue();
            double sourceLon = place.getLongitude().doubleValue();
            if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180
                    || Math.abs(sourceLat) > 90 || Math.abs(sourceLon) > 180 || lat == 0 && lon == 0) {
                return null;
            }
            double a = Math.pow(Math.sin(Math.toRadians(lat - sourceLat) / 2), 2)
                    + Math.cos(Math.toRadians(sourceLat)) * Math.cos(Math.toRadians(lat))
                    * Math.pow(Math.sin(Math.toRadians(lon - sourceLon) / 2), 2);
            return 6_371_000 * 2 * Math.asin(Math.sqrt(Math.min(1, a)));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    // overview 정리 및 빈 설명의 null 처리
    private static String plainText(String value) {
        String text = HtmlUtils.htmlUnescape(value.replaceAll("<[^>]*>", " "))
                .replaceAll("\\s+", " ").strip();
        return StringUtils.hasText(text) ? text : null;
    }

    private static TourPlaceResponse.MatchResult result(Place place, TourPlaceMatchStatus status, TourPlaceMatchReason reason,
                                                        String contentId, String overview, int requests, boolean limitReached,
                                                        Map<String, TourPlaceResponse.Candidate> candidates) {
        return new TourPlaceResponse.MatchResult(place.getExternalPlaceId(), status, reason, contentId, overview,
                requests, limitReached, List.copyOf(candidates.values()));
    }
}
