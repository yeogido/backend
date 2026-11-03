package com.yeogido.backend.domain.placeenrichment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.content.client.TourApiClient;
import com.yeogido.backend.domain.content.exception.ContentErrorCode;
import com.yeogido.backend.domain.placeenrichment.dto.TourRegionCode;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.global.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TourRegionCodeMapperTest {

    private final TourApiClient client = mock(TourApiClient.class);
    private final RegionRepository repository = mock(RegionRepository.class);
    private final TourRegionCodeMapper mapper = new TourRegionCodeMapper(client, repository);
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(client.items(any())).thenCallRealMethod();
        when(client.totalCount(any())).thenCallRealMethod();
    }

    @Test
    void prefersCompositeDistrictOverCityAndReusesCompleteCodeLists() {
        when(client.legalDistrictCodes(null, 1, 100)).thenReturn(page(1, "41", "경기도"));
        when(client.legalDistrictCodes("41", 1, 100))
                .thenReturn(page(2, "110", "수원시", "117", "수원시 영통구"));

        assertThat(mapper.resolve("경기 수원시 영통구 가상로 1"))
                .contains(new TourRegionCode("41", "117"));
        assertThat(mapper.resolve("경기도 수원시 영통구 가상로 2"))
                .contains(new TourRegionCode("41", "117"));

        verify(client).legalDistrictCodes(null, 1, 100);
        verify(client).legalDistrictCodes("41", 1, 100);
    }

    @Test
    void usesParentProvinceToDisambiguateDistrictAndStoresRegionMapping() {
        when(client.legalDistrictCodes(null, 1, 100))
                .thenReturn(page(2, "11", "서울특별시", "26", "부산광역시"));
        when(client.legalDistrictCodes("26", 1, 100)).thenReturn(page(1, "110", "중구"));
        Region parent = Region.builder().name("부산광역시").build();
        Region stored = Region.builder().id(1L).name("중구").parent(parent).build();
        when(repository.findById(1L)).thenReturn(Optional.of(stored));

        assertThat(mapper.resolve(Region.builder().id(1L).build(), "서울 중구", null))
                .contains(new TourRegionCode("26", "110"));
        assertThat(stored.getTourLdongRegnCd()).isEqualTo("26");
        assertThat(stored.getTourLdongSignguCd()).isEqualTo("110");
        verify(client, never()).legalDistrictCodes("11", 1, 100);
    }

    @Test
    void prioritizesLatestStoredRegionCodesOverAddressAndDetachedCodes() {
        Region stored = Region.builder().id(1L).tourLdongRegnCd("26").tourLdongSignguCd("110").build();
        Region detached = Region.builder().id(1L).tourLdongRegnCd("11").tourLdongSignguCd("140").build();
        when(repository.findById(1L)).thenReturn(Optional.of(stored));

        assertThat(mapper.resolve(detached, "서울 중구", "서울 중구"))
                .contains(new TourRegionCode("26", "110"));

        verifyNoInteractions(client);
    }

    @Test
    void fallsBackToLotAddressWhenRoadAddressCannotBeResolved() {
        seoulCodes();

        assertThat(mapper.resolve("알 수 없는 주소", "서울 마포구 가상동 1"))
                .contains(new TourRegionCode("11", "440"));
    }

    @Test
    void supplementsRoadProvinceWithLotDistrictOnlyWithinSameProvince() {
        seoulAndBusanCodes();

        assertThat(mapper.resolve("서울", "서울 마포구 가상동 1"))
                .contains(new TourRegionCode("11", "440"));
        assertThat(mapper.resolve("서울", "부산 중구 가상동 1"))
                .contains(new TourRegionCode("11", null));
    }

    @Test
    void supplementsRegionProvinceWithoutSavingPlaceSpecificDistrict() {
        seoulAndBusanCodes();
        Region stored = Region.builder().id(1L).tourLdongRegnCd("11").build();
        when(repository.findById(1L)).thenReturn(Optional.of(stored));

        assertThat(mapper.resolve(Region.builder().id(1L).build(), "부산 중구", "서울 마포구"))
                .contains(new TourRegionCode("11", "440"));
        assertThat(stored.getTourLdongSignguCd()).isNull();
        assertThat(mapper.resolve(Region.builder().id(1L).build(), "부산 중구", null))
                .contains(new TourRegionCode("11", null));
    }

    @Test
    void readsEveryProvinceAndDistrictPageBeforeMatching() {
        when(client.legalDistrictCodes(null, 1, 100)).thenReturn(page(2, "11", "서울특별시"));
        when(client.legalDistrictCodes(null, 2, 100)).thenReturn(page(2, "26", "부산광역시"));
        when(client.legalDistrictCodes("26", 1, 100)).thenReturn(page(2, "110", "중구"));
        when(client.legalDistrictCodes("26", 2, 100)).thenReturn(page(2, "140", "서구"));

        assertThat(mapper.resolve("부산 서구 가상로 1"))
                .contains(new TourRegionCode("26", "140"));

        verify(client).legalDistrictCodes(null, 2, 100);
        verify(client).legalDistrictCodes("26", 2, 100);
    }

    @Test
    void retriesProvinceLookupAfterApiFailure() {
        when(client.legalDistrictCodes(null, 1, 100))
                .thenThrow(new GeneralException(ContentErrorCode.TOUR_API_ERROR))
                .thenReturn(page(1, "11", "서울특별시"));

        assertApiError(() -> mapper.resolve("서울"));
        assertThat(mapper.resolve("서울")).contains(new TourRegionCode("11", null));
        verify(client, times(2)).legalDistrictCodes(null, 1, 100);
    }

    @Test
    void doesNotCacheIncompleteProvinceList() {
        when(client.legalDistrictCodes(null, 1, 100))
                .thenReturn(page(2, "11", "서울특별시"), page(1, "11", "서울특별시"));
        when(client.legalDistrictCodes(null, 2, 100)).thenReturn(page(2));

        assertApiError(() -> mapper.resolve("서울"));
        assertThat(mapper.resolve("서울")).contains(new TourRegionCode("11", null));
        verify(client, times(2)).legalDistrictCodes(null, 1, 100);
    }

    @Test
    void retriesDistrictLookupAfterApiFailureWithoutReloadingProvinces() {
        when(client.legalDistrictCodes(null, 1, 100)).thenReturn(page(1, "11", "서울특별시"));
        when(client.legalDistrictCodes("11", 1, 100))
                .thenThrow(new GeneralException(ContentErrorCode.TOUR_API_ERROR))
                .thenReturn(page(1, "440", "마포구"));

        assertApiError(() -> mapper.resolve("서울 마포구"));
        assertThat(mapper.resolve("서울 마포구")).contains(new TourRegionCode("11", "440"));
        verify(client).legalDistrictCodes(null, 1, 100);
        verify(client, times(2)).legalDistrictCodes("11", 1, 100);
    }

    @Test
    void doesNotCacheIncompleteDistrictList() {
        when(client.legalDistrictCodes(null, 1, 100)).thenReturn(page(1, "11", "서울특별시"));
        when(client.legalDistrictCodes("11", 1, 100))
                .thenReturn(page(2, "440", "마포구"), page(1, "440", "마포구"));
        when(client.legalDistrictCodes("11", 2, 100)).thenReturn(page(2));

        assertApiError(() -> mapper.resolve("서울 마포구"));
        assertThat(mapper.resolve("서울 마포구")).contains(new TourRegionCode("11", "440"));
        verify(client).legalDistrictCodes(null, 1, 100);
        verify(client, times(2)).legalDistrictCodes("11", 1, 100);
    }

    private void seoulCodes() {
        when(client.legalDistrictCodes(null, 1, 100)).thenReturn(page(1, "11", "서울특별시"));
        when(client.legalDistrictCodes("11", 1, 100)).thenReturn(page(1, "440", "마포구"));
    }

    private void seoulAndBusanCodes() {
        when(client.legalDistrictCodes(null, 1, 100))
                .thenReturn(page(2, "11", "서울특별시", "26", "부산광역시"));
        when(client.legalDistrictCodes("11", 1, 100)).thenReturn(page(1, "440", "마포구"));
        when(client.legalDistrictCodes("26", 1, 100)).thenReturn(page(1, "110", "중구"));
    }

    private void assertApiError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(GeneralException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ContentErrorCode.TOUR_API_ERROR));
    }

    private JsonNode page(int total, String... entries) {
        var response = json.createObjectNode();
        var body = response.putObject("response").putObject("body");
        body.put("totalCount", total);
        var items = body.putObject("items").putArray("item");
        for (int index = 0; index < entries.length; index += 2) {
            items.addObject().put("code", entries[index]).put("name", entries[index + 1]);
        }
        return response;
    }
}
