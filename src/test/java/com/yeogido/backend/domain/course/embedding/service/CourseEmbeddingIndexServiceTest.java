package com.yeogido.backend.domain.course.embedding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.course.client.CourseEmbeddingClient;
import com.yeogido.backend.domain.course.embedding.entity.CourseEmbedding;
import com.yeogido.backend.domain.course.embedding.repository.CourseEmbeddingDocumentRepository;
import com.yeogido.backend.domain.course.embedding.repository.CourseEmbeddingDocumentRepository.CourseDocument;
import com.yeogido.backend.domain.course.embedding.repository.CourseEmbeddingRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CourseEmbeddingIndexServiceTest {

    private final CourseEmbeddingDocumentRepository documents = mock(CourseEmbeddingDocumentRepository.class);
    private final CourseEmbeddingRepository embeddings = mock(CourseEmbeddingRepository.class);
    private final CourseEmbeddingClient client = mock(CourseEmbeddingClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private CourseEmbeddingIndexService service;

    @BeforeEach
    void setUp() {
        when(client.isEnabled()).thenReturn(true);
        when(client.getModel()).thenReturn("model");
        when(client.getDimensions()).thenReturn(2);
        when(embeddings.findAllById(anyList())).thenReturn(List.of());
        service = indexService(20);
    }

    @Test
    void embedsOnlyNewOrChangedSourcesAndDoesNotHoldTransactionDuringProviderCall() {
        var first = document(1, "unchanged");
        var second = document(2, "changed");
        var third = document(3, "new");
        when(documents.findCourseIdsAfter(0, 100)).thenReturn(List.of(1L, 2L, 3L));
        when(documents.findDocuments(List.of(1L, 2L, 3L))).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            return Map.of(1L, first, 2L, second, 3L, third);
        });
        when(documents.findDocuments(List.of(2L, 3L))).thenReturn(Map.of(2L, second, 3L, third));
        when(embeddings.findAllById(List.of(1L, 2L, 3L))).thenReturn(List.of(
                row(1, "unchanged", "[0.6,0.8]"), row(2, "old", "[0.6,0.8]")));
        when(client.embedDocuments(List.of("text-changed", "text-new"))).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return List.of(List.of(0.6, 0.8), List.of(1.0, 0.0));
        });
        when(embeddings.save(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            return invocation.getArgument(0);
        });

        assertThat(service.reindexNextBatch()).isEqualTo(2);
        var saved = ArgumentCaptor.forClass(CourseEmbedding.class);
        verify(embeddings, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(CourseEmbedding::getCourseId).containsExactly(2L, 3L);
        assertThat(saved.getValue().getSourceHash()).isEqualTo("new");
    }

    @Test
    void ignoresResponsesSupersededByEditOrDeleteWhileEmbedding() {
        when(documents.findCourseIdsAfter(0, 100)).thenReturn(List.of(1L, 2L));
        when(documents.findDocuments(List.of(1L, 2L))).thenReturn(
                Map.of(1L, document(1, "old"), 2L, document(2, "deleted")),
                Map.of(1L, document(1, "edited")));
        when(client.embedDocuments(anyList())).thenReturn(List.of(List.of(1.0, 0.0), List.of(0.0, 1.0)));

        assertThat(service.reindexNextBatch()).isZero();
        verify(embeddings, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void providerFailureDoesNotStarveHigherCourseIdsAndScanWraps() {
        service = indexService(1);
        when(documents.findCourseIdsAfter(0, 100)).thenReturn(List.of(1L, 2L));
        when(documents.findCourseIdsAfter(1, 100)).thenReturn(List.of(2L));
        when(documents.findCourseIdsAfter(2, 100)).thenReturn(List.of());
        when(documents.findDocuments(List.of(1L, 2L))).thenReturn(Map.of(1L, document(1, "one"), 2L, document(2, "two")));
        when(documents.findDocuments(List.of(2L))).thenReturn(Map.of(2L, document(2, "two")));
        when(client.embedDocuments(List.of("text-one"))).thenThrow(new IllegalStateException("unavailable"));
        when(client.embedDocuments(List.of("text-two"))).thenReturn(List.of(List.of(1.0, 0.0)));

        assertThatThrownBy(service::reindexNextBatch).isInstanceOf(IllegalStateException.class);
        assertThat(service.reindexNextBatch()).isEqualTo(1);
        assertThatThrownBy(service::reindexNextBatch).isInstanceOf(IllegalStateException.class);
        verify(documents, org.mockito.Mockito.times(2)).findCourseIdsAfter(0, 100);
    }

    @Test
    void corruptVectorIsReindexedEvenWhenSourceHashMatches() {
        when(documents.findCourseIdsAfter(0, 100)).thenReturn(List.of(1L));
        when(documents.findDocuments(List.of(1L))).thenReturn(Map.of(1L, document(1, "same")));
        when(embeddings.findAllById(List.of(1L))).thenReturn(List.of(row(1, "same", "[\"bad\",0]")));
        when(client.embedDocuments(anyList())).thenReturn(List.of(List.of(1.0, 0.0)));

        assertThat(service.reindexNextBatch()).isEqualTo(1);
        verify(client).embedDocuments(List.of("text-same"));
    }

    @Test
    void readsOnlyCurrentModelDimensionsAndSkipsMalformedVectors() {
        when(embeddings.findActiveVectors("model", 2)).thenReturn(List.of(
                row(1, "hash", "[0.6,0.8]"), row(2, "hash", "[]"), row(3, "hash", "[0,0]"),
                row(4, "hash", "[1e999,0]"), row(5, "hash", "[true,0]")));

        var result = service.findIndexedVectors();
        assertThat(result).extracting(CourseEmbeddingIndexService.IndexedVector::courseId).containsExactly(1L);
        assertThat(result.getFirst().vector()).containsExactly(0.6, 0.8);
        assertThatThrownBy(() -> result.getFirst().vector().add(2.0)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void disabledFeatureDoesNotReadOrCallProvider() {
        when(client.isEnabled()).thenReturn(false);
        assertThat(service.reindexNextBatch()).isZero();
        assertThat(service.findIndexedVectors()).isEmpty();
        verifyNoInteractions(documents, embeddings);
    }

    private CourseEmbeddingIndexService indexService(int batch) {
        return new CourseEmbeddingIndexService(documents, embeddings, client, mapper, new TestTransactionManager(), batch, 100);
    }

    private CourseDocument document(long id, String hash) {
        return new CourseDocument(id, "text-" + hash, hash);
    }

    private CourseEmbedding row(long id, String hash, String vector) {
        return new CourseEmbedding(id, "model", 2, hash, vector, LocalDateTime.now());
    }

    private static class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }
}
