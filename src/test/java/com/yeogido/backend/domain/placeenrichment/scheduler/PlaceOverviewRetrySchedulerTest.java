package com.yeogido.backend.domain.placeenrichment.scheduler;

import com.yeogido.backend.domain.place.repository.PlaceRepository;
import com.yeogido.backend.domain.placeenrichment.service.PlaceOverviewEnrichmentService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceOverviewRetrySchedulerTest {

  @Mock PlaceRepository placeRepository;
  @Mock PlaceOverviewEnrichmentService enrichmentService;
  @InjectMocks PlaceOverviewRetryScheduler scheduler;

  @Test
  void continuesWithNextPlaceWhenRetryFails() {
    when(placeRepository.findOverviewRetryCandidates(any(), eq(2), any()))
        .thenReturn(List.of(10L, 20L));
    doThrow(new IllegalStateException("external API failure")).when(enrichmentService).enrich(10L);

    scheduler.retryFailedOverviews();

    var ordered = inOrder(enrichmentService);
    ordered.verify(enrichmentService).enrich(10L);
    ordered.verify(enrichmentService).enrich(20L);
  }

}
