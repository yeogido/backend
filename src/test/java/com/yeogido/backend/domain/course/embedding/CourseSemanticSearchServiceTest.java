package com.yeogido.backend.domain.course.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.yeogido.backend.domain.course.client.CourseEmbeddingClient;
import com.yeogido.backend.domain.course.dto.CourseSearchDTO.Interpretation;
import com.yeogido.backend.domain.course.embedding.service.CourseEmbeddingIndexService;
import com.yeogido.backend.domain.course.embedding.service.CourseEmbeddingIndexService.IndexedVector;
import com.yeogido.backend.domain.course.repository.CourseSemanticSearchSessionRepository;
import com.yeogido.backend.global.exception.GeneralException;
import java.util.List;
import org.junit.jupiter.api.Test;

class CourseSemanticSearchServiceTest {
    private final CourseEmbeddingClient client = mock(CourseEmbeddingClient.class);
    private final CourseEmbeddingIndexService index = mock(CourseEmbeddingIndexService.class);
    private final CourseSemanticSearchSessionRepository sessions = mock(CourseSemanticSearchSessionRepository.class);
    private final CourseSemanticSearchService search = new CourseSemanticSearchService(client, index, sessions, 0.65);

    @Test
    void semanticSimilarityFindsDifferentWordingAndRejectsUnrelatedVectorsWithoutTopKTruncation() {
        var interpretation = new Interpretation(null, List.of("조용한", "바다"));
        when(client.embedQuery("조용한 바다")).thenReturn(List.of(1.0, 0.0));
        // Stored document vectors represent '한적한 해변 산책', '고요한 해안', '시끌벅적한 놀이공원'.
        when(index.findIndexedVectors()).thenReturn(List.of(
                new IndexedVector(1L, List.of(0.9, 0.1)),
                new IndexedVector(2L, List.of(0.8, 0.2)),
                new IndexedVector(3L, List.of(0.0, 1.0))));
        when(sessions.save("조용한 바다", List.of(1L, 2L))).thenReturn("session");

        var result = search.search(interpretation);

        assertThat(result.courseIds()).containsExactly(1L, 2L);
        assertThat(result.sessionId()).isEqualTo("session");
    }

    @Test
    void noIndexedOrSimilarCoursesAreAnExplicitEmptySnapshot() {
        when(client.embedQuery("데이트")).thenReturn(List.of(1.0, 0.0));
        when(index.findIndexedVectors()).thenReturn(List.of());
        when(sessions.save("데이트", List.of())).thenReturn("empty");
        assertThat(search.search(new Interpretation(null, List.of("데이트"))).courseIds()).isEmpty();
        verify(sessions).save("데이트", List.of());
    }

    @Test
    void nextPageRestoresSnapshotWithoutEmbeddingAgainEvenWhenIndexChanged() {
        when(sessions.find("saved", "비 오는 날")).thenReturn(List.of(4L));
        var interpretation = new Interpretation(null, List.of("비 오는 날"), null, List.of(), "saved");
        assertThat(search.restore(interpretation)).containsExactly(4L);
        verifyNoInteractions(client, index);
    }

    @Test
    void providerFailureDoesNotFallBackToStringSearch() {
        when(client.embedQuery(anyString())).thenThrow(new GeneralException(
                com.yeogido.backend.domain.course.exception.CourseErrorCode.COURSE_SEARCH_API_ERROR));
        assertThatThrownBy(() -> search.search(new Interpretation(null, List.of("조용한"))))
                .isInstanceOf(GeneralException.class);
        verifyNoInteractions(index, sessions);
    }

    @Test
    void cosinePreservesDirectionForVerySmallAndLargeFiniteVectors() {
        for (double scale : new double[] {1e-300, 1e300}) {
            assertThat(CourseSemanticSearchService.cosine(List.of(scale, 0.0), List.of(1.0, 0.0))).isEqualTo(1);
            assertThat(CourseSemanticSearchService.cosine(List.of(scale, 0.0), List.of(-1.0, 0.0))).isEqualTo(-1);
            assertThat(CourseSemanticSearchService.cosine(List.of(scale, 0.0), List.of(0.0, 1.0))).isZero();
        }
    }

    @Test
    void cosineNormalizesAndRejectsMalformedOrZeroVectors() {
        assertThat(CourseSemanticSearchService.cosine(List.of(2.0, 0.0), List.of(4.0, 0.0))).isEqualTo(1);
        for (List<Double> invalid : List.of(List.<Double>of(), List.of(1.0), List.of(0.0, 0.0),
                List.of(Double.NaN, 1.0), List.of(Double.POSITIVE_INFINITY, 1.0))) {
            assertThatThrownBy(() -> CourseSemanticSearchService.cosine(List.of(1.0, 0.0), invalid))
                    .isInstanceOf(GeneralException.class);
        }
    }
}
