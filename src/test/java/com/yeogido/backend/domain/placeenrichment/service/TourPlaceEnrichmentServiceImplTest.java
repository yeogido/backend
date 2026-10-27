package com.yeogido.backend.domain.placeenrichment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.content.client.TourApiClient;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.placeenrichment.dto.TourRegionCode;
import com.yeogido.backend.domain.placeenrichment.dto.response.TourPlaceResponse;
import com.yeogido.backend.global.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.yeogido.backend.domain.placeenrichment.enums.TourPlaceMatchReason.*;
import static com.yeogido.backend.domain.placeenrichment.enums.TourPlaceMatchStatus.*;
import static com.yeogido.backend.domain.placeenrichment.exception.PlaceEnrichmentErrorCode.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TourPlaceEnrichmentServiceImplTest {
    private final TourApiClient client = mock(TourApiClient.class);
    private final TourRegionCodeMapper mapper = mock(TourRegionCodeMapper.class);
    private final ObjectMapper json = new ObjectMapper();
    private final TourPlaceEnrichmentServiceImpl service = new TourPlaceEnrichmentServiceImpl(client, mapper);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxSearchRequests", 12);
        ReflectionTestUtils.setField(service, "matchDistanceMeters", 100.0);
        when(mapper.resolve(any(Place.class))).thenReturn(Optional.of(new TourRegionCode("11", "440")));
        when(client.items(any())).thenAnswer(invocation -> {
            JsonNode items = ((JsonNode) invocation.getArgument(0)).path("items");
            return items.isArray() ? items.valueStream().toList() : List.of();
        });
        when(client.totalCount(any())).thenAnswer(invocation ->
                ((JsonNode) invocation.getArgument(0)).path("total").asInt());
    }

    @Test
    void matchesByNameAndCoordinatesAndReturnsPlainOverview() throws Exception {
        search(page(1, candidate("1", "샘플식당 기준점", "다른 표기의 주소", "127.000000", "37.500000")));
        detail("1", "<p>샘플 메뉴 &amp; 음료</p>");
        TourPlaceResponse.MatchResult result = service.enrich(place("샘플식당 기준점"));
        assertThat(result.matchStatus()).isEqualTo(MATCHED);
        assertThat(result.contentId()).isEqualTo("1");
        assertThat(result.matchReason()).isEqualTo(NAME_AND_LOCATION_MATCHED);
        assertThat(json.valueToTree(result).path("matchReason").asText()).isEqualTo("NAME_AND_LOCATION_MATCHED");
        assertThat(result.overview()).isEqualTo("샘플 메뉴 & 음료");
        assertThat(result.searchRequestCount()).isEqualTo(1);
        verify(client).searchPlaces("샘플식당 기준점", "11", "440", 1, 20);
    }

    @Test
    void addressCanMatchWithoutCoordinatesAndIgnoresFloor() throws Exception {
        search(page(1, candidate("1", "샘플식당 기준점", "서울특별시 마포구 가상로 45 지하1층", "", "")));
        detail("1", "소개");
        assertThat(service.enrich(place("샘플식당 기준점")).matchStatus()).isEqualTo(MATCHED);
    }

    @Test
    void differentBranchIsNotConfirmedEvenAtSameAddress() throws Exception {
        search(page(1, candidate("1", "샘플식당 비교점", "서울 마포구 가상로 45", "", "")));
        assertThat(service.enrich(place("샘플식당 기준점")).matchStatus()).isEqualTo(AMBIGUOUS);
        verify(client, never()).detailCommon(any());
    }

    @Test
    void differentBuildingNumberDoesNotMatchWithoutCoordinates() throws Exception {
        search(page(1, candidate("1", "샘플식당 기준점", "서울 마포구 가상로 450", "", "")));
        assertThat(service.enrich(place("샘플식당 기준점")).matchStatus()).isEqualTo(AMBIGUOUS);
    }

    @Test
    void farCoordinatesPreventAddressBasedConfirmation() throws Exception {
        search(page(1, candidate("1", "샘플식당 기준점", "서울 마포구 가상로 45", "129", "35")));
        assertThat(service.enrich(place("샘플식당 기준점")).matchStatus()).isEqualTo(AMBIGUOUS);
    }

    @Test
    void duplicateStrongCandidatesAcrossPagesRemainAmbiguous() throws Exception {
        when(client.searchPlaces(anyString(), any(), any(), anyInt(), anyInt()))
                .thenReturn(page(2, candidate("1", "샘플식당 기준점", "서울 마포구 가상로 45", "", "")),
                        page(2, candidate("2", "샘플식당 기준점", "서울 마포구 가상로 45", "", "")));
        TourPlaceResponse.MatchResult result = service.enrich(place("샘플식당 기준점"));
        assertThat(result.matchStatus()).isEqualTo(AMBIGUOUS);
        assertThat(result.matchReason()).isEqualTo(MULTIPLE_MATCHES);
        verify(client, never()).detailCommon(any());
    }

    @Test
    void removesBranchSuffixAndThenRelaxesDistrict() throws Exception {
        JsonNode empty = page(0, "");
        JsonNode found = page(1, candidate("1", "샘플식당", "서울 마포구 가상로 45", "", ""));
        when(client.searchPlaces(anyString(), any(), any(), anyInt(), anyInt())).thenReturn(empty);
        when(client.searchPlaces("샘플식당", "11", null, 1, 20)).thenReturn(found);
        detail("1", "소개");
        assertThat(service.enrich(place("샘플식당 기준점")).matchStatus()).isEqualTo(MATCHED);
        verify(client).searchPlaces("샘플식당", "11", "440", 1, 20);
        verify(client).searchPlaces("샘플식당", "11", null, 1, 20);
    }

    @Test
    void limitDoesNotConfirmCandidateBeforeFinishingPages() throws Exception {
        ReflectionTestUtils.setField(service, "maxSearchRequests", 1);
        search(page(2, candidate("1", "샘플식당 기준점", "서울 마포구 가상로 45", "", "")));
        TourPlaceResponse.MatchResult result = service.enrich(place("샘플식당 기준점"));
        assertThat(result.matchStatus()).isEqualTo(AMBIGUOUS);
        assertThat(result.searchLimitReached()).isTrue();
        assertThat(result.matchReason()).isEqualTo(SEARCH_LIMIT_REACHED);
        verify(client, never()).detailCommon(any());
    }

    @Test
    void emptySearchIsUnmatchedAndDoesNotRequestDetails() throws Exception {
        search(page(0, ""));
        TourPlaceResponse.MatchResult result = service.enrich(place("샘플식당 기준점"));
        assertThat(result.matchStatus()).isEqualTo(UNMATCHED);
        assertThat(result.matchReason()).isEqualTo(NO_MATCH);
        verify(client, never()).detailCommon(any());
    }

    @Test
    void missingOverviewRemainsMatched() throws Exception {
        search(page(1, candidate("1", "샘플식당 기준점", "서울 마포구 가상로 45", "", "")));
        detail("1", "");
        TourPlaceResponse.MatchResult result = service.enrich(place("샘플식당 기준점"));
        assertThat(result.matchStatus()).isEqualTo(MATCHED);
        assertThat(result.overview()).isNull();
        assertThat(result.matchReason()).isEqualTo(MATCHED_WITHOUT_OVERVIEW);
    }

    @Test
    void unexpectedEmptyPageIsAnError() throws Exception {
        search(page(2, ""));
        assertThatThrownBy(() -> service.enrich(place("샘플식당 기준점")))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void apiFailureIsNotUnmatched() {
        when(client.searchPlaces(anyString(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("API unavailable"));
        assertThatThrownBy(() -> service.enrich(place("샘플식당 기준점")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void detailWithDifferentContentIdIsAnError() throws Exception {
        search(page(1, candidate("1", "샘플식당 기준점", "서울 마포구 가상로 45", "", "")));
        when(client.detailCommon("1")).thenReturn(page(1, "{\"contentid\":\"other\"}"));
        assertThatThrownBy(() -> service.enrich(place("샘플식당 기준점")))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void matchingCandidateOnSecondPageIsReturned() throws Exception {
        when(client.searchPlaces(anyString(), any(), any(), anyInt(), anyInt()))
                .thenReturn(page(2, candidate("other", "다른 식당", "다른 주소", "", "")),
                        page(2, candidate("1", "샘플식당 기준점", "서울 마포구 가상로 45", "", "")));
        detail("1", "소개");
        TourPlaceResponse.MatchResult result = service.enrich(place("샘플식당 기준점"));
        assertThat(result.matchStatus()).isEqualTo(MATCHED);
        assertThat(result.searchRequestCount()).isEqualTo(2);
    }

    @Test
    void nameAloneDoesNotConfirmLocation() throws Exception {
        search(page(1, candidate("1", "샘플식당 기준점", "", "", "")));
        TourPlaceResponse.MatchResult result = service.enrich(place("샘플식당 기준점"));
        assertThat(result.matchStatus()).isEqualTo(AMBIGUOUS);
        assertThat(result.matchReason()).isEqualTo(INSUFFICIENT_EVIDENCE);
        verify(client, never()).detailCommon(any());
    }

    @Test
    void missingRegionUsesUnfilteredSearch() throws Exception {
        when(mapper.resolve(any(Place.class))).thenReturn(Optional.empty());
        search(page(0, ""));
        assertThat(service.enrich(place("샘플식당 기준점")).matchStatus()).isEqualTo(UNMATCHED);
        verify(client).searchPlaces("샘플식당 기준점", null, null, 1, 20);
    }

    @Test
    void missingNameUsesPlaceErrorCode() {
        for (String name : new String[]{null, "", "  "}) {
            assertThatThrownBy(() -> service.enrich(place(name)))
                    .isInstanceOfSatisfying(GeneralException.class,
                            exception -> assertThat(exception.getErrorCode()).isEqualTo(PLACE_NAME_REQUIRED));
        }
        verifyNoInteractions(client, mapper);
    }

    @Test
    void invalidSearchLimitUsesPlaceErrorCode() {
        ReflectionTestUtils.setField(service, "maxSearchRequests", 0);
        assertThatThrownBy(() -> service.enrich(place("샘플식당 기준점")))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(INVALID_TOUR_PLACE_SEARCH_CONFIG));
        verifyNoInteractions(client, mapper);
    }

    @Test
    void invalidMatchDistanceUsesPlaceErrorCode() {
        for (double distance : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            ReflectionTestUtils.setField(service, "matchDistanceMeters", distance);
            assertThatThrownBy(() -> service.enrich(place("샘플식당 기준점")))
                    .isInstanceOfSatisfying(GeneralException.class,
                            exception -> assertThat(exception.getErrorCode()).isEqualTo(INVALID_TOUR_PLACE_SEARCH_CONFIG));
        }
        verifyNoInteractions(client, mapper);
    }

    private Place place(String name) {
        return Place.builder().externalPlaceId("test-place-1").name(name)
                .roadAddress("서울 마포구 가상로 45").lotAddress("서울 마포구 가상동 383")
                .latitude(new BigDecimal("37.500000"))
                .longitude(new BigDecimal("127.000000")).build();
    }

    private void search(JsonNode response) {
        when(client.searchPlaces(anyString(), any(), any(), anyInt(), anyInt())).thenReturn(response);
    }

    private void detail(String id, String overview) throws Exception {
        when(client.detailCommon(id)).thenReturn(page(1,
                json.createObjectNode().put("contentid", id).put("overview", overview).toString()));
    }

    private JsonNode page(int total, String items) throws Exception {
        return json.readTree("{\"total\":" + total + ",\"items\":[" + items + "]}");
    }

    private String candidate(String id, String name, String address, String lon, String lat) {
        return json.createObjectNode().put("contentid", id).put("title", name).put("addr1", address)
                .put("mapx", lon).put("mapy", lat).toString();
    }
}
