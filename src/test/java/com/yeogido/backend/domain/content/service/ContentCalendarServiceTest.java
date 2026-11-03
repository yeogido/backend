package com.yeogido.backend.domain.content.service;

import com.yeogido.backend.domain.content.dto.ContentReqDTO;
import com.yeogido.backend.domain.content.repository.ContentCalendarQueryRepository;
import com.yeogido.backend.domain.content.enums.ContentSort;
import com.yeogido.backend.domain.content.enums.ContentCategory;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import java.math.BigDecimal;
import java.util.Optional;
import com.yeogido.backend.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentCalendarServiceTest {
  @Mock ContentCalendarQueryRepository contentRepository;
  @Mock RegionRepository regionRepository;
  @InjectMocks ContentServiceImpl contentService;

  @Test
  void rejectsNullRequest() {
    assertThatThrownBy(() -> contentService.getCalendarContents(null, null))
        .isInstanceOf(GeneralException.class);
    verifyNoInteractions(contentRepository);
  }
  @ParameterizedTest
  @CsvSource({"2026-10-15,2026-10-15", "2026-10-10,2026-10-20",
      "2026-10-30,2026-11-02", "2026-12-31,2027-01-02", "2028-02-29,2028-02-29",
      "2026-10-01,2026-10-31", "2026-02-01,2026-02-28", "2028-02-01,2028-02-29"})
  void queriesExactDateRange(String start, String end) {
    LocalDate first = LocalDate.parse(start);
    LocalDate last = LocalDate.parse(end);
    when(contentRepository.findCalendarContents(new ContentReqDTO.ContentCalendarReq(first,last), List.of(), null, null))
        .thenReturn(List.of());
    var result = contentService.getCalendarContents(
        new ContentReqDTO.ContentCalendarReq(first, last), null);
    assertThat(result.startDate()).isEqualTo(first);
    assertThat(result.endDate()).isEqualTo(last);
    assertThat(result.totalCount()).isZero();
    assertThat(result.contents()).isEmpty();
    verify(contentRepository).findCalendarContents(new ContentReqDTO.ContentCalendarReq(first,last), List.of(), null, null);
  }

  @Test
  void rejectsMissingReversedAndOutOfRangeDates() {
    LocalDate day = LocalDate.of(2026,10,15);
    var requests = List.of(
        new ContentReqDTO.ContentCalendarReq(null,null),
        new ContentReqDTO.ContentCalendarReq(day,null),
        new ContentReqDTO.ContentCalendarReq(null,day),
        new ContentReqDTO.ContentCalendarReq(day.plusDays(1),day),
        new ContentReqDTO.ContentCalendarReq(LocalDate.of(0,1,1),day),
        new ContentReqDTO.ContentCalendarReq(day,LocalDate.of(10000,1,1))
    );
    for (var request : requests) {
      assertThatThrownBy(() -> contentService.getCalendarContents(request,null))
          .isInstanceOf(GeneralException.class);
    }
    verifyNoInteractions(contentRepository);
  }

  @Test
  void forwardsCategorySortAndGpsCoordinates() {
    LocalDate date = LocalDate.of(2026,10,15);
    var req = new ContentReqDTO.ContentCalendarReq(date,date,ContentCategory.EXPERIENCE,
        ContentSort.DISTANCE,null,37.5,127.0);
    when(contentRepository.findCalendarContents(req,List.of(),37.5,127.0)).thenReturn(List.of());
    assertThat(contentService.getCalendarContents(req,null).contents()).isEmpty();
    verify(contentRepository).findCalendarContents(req,List.of(),37.5,127.0);
  }

  @Test
  void fallsBackToSelectedRegionCenterForDistance() {
    LocalDate date = LocalDate.of(2026,10,15);
    var req = new ContentReqDTO.ContentCalendarReq(date,date,null,ContentSort.DISTANCE,4L,null,null);
    Region region = Region.builder().id(4L).type(RegionType.SUB_REGION)
        .latitude(new BigDecimal("37.5")).longitude(new BigDecimal("127.0")).build();
    when(regionRepository.findById(4L)).thenReturn(Optional.of(region));
    when(contentRepository.findCalendarContents(req,List.of(4L),37.5,127.0)).thenReturn(List.of());
    contentService.getCalendarContents(req,null);
    verify(contentRepository).findCalendarContents(req,List.of(4L),37.5,127.0);
  }

  @Test
  void includesSubregionsWhenRegionIsSelected() {
    LocalDate date = LocalDate.of(2026,10,15);
    var req = new ContentReqDTO.ContentCalendarReq(date,date,null,ContentSort.LIKE,4L,null,null);
    Region region = Region.builder().id(4L).type(RegionType.REGION).build();
    when(regionRepository.findById(4L)).thenReturn(Optional.of(region));
    when(regionRepository.findByParentIdAndTypeOrderByNameAsc(4L,RegionType.SUB_REGION))
        .thenReturn(List.of(Region.builder().id(5L).build()));
    when(contentRepository.findCalendarContents(req,List.of(4L,5L),null,null)).thenReturn(List.of());
    contentService.getCalendarContents(req,null);
    verify(contentRepository).findCalendarContents(req,List.of(4L,5L),null,null);
  }

  @Test
  void rejectsInvalidCoordinatesAndMissingDistanceOrigin() {
    LocalDate date = LocalDate.of(2026,10,15);
    for (var req : List.of(
        new ContentReqDTO.ContentCalendarReq(date,date,null,ContentSort.DISTANCE,null,null,null),
        new ContentReqDTO.ContentCalendarReq(date,date,null,ContentSort.DISTANCE,null,37.5,null),
        new ContentReqDTO.ContentCalendarReq(date,date,null,ContentSort.DISTANCE,null,null,127.0),
        new ContentReqDTO.ContentCalendarReq(date,date,null,ContentSort.DISTANCE,null,91.0,127.0),
        new ContentReqDTO.ContentCalendarReq(date,date,null,ContentSort.DISTANCE,null,37.0,181.0),
        new ContentReqDTO.ContentCalendarReq(date,date,null,ContentSort.DISTANCE,null,Double.NaN,127.0),
        new ContentReqDTO.ContentCalendarReq(date,date,null,ContentSort.DISTANCE,null,37.0,Double.POSITIVE_INFINITY)
    )) {
      assertThatThrownBy(() -> contentService.getCalendarContents(req,null)).isInstanceOf(GeneralException.class);
    }
    verifyNoInteractions(contentRepository);
  }

}
