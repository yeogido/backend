package com.yeogido.backend.domain.course.embedding.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.course.client.CourseEmbeddingClient;
import com.yeogido.backend.domain.course.embedding.entity.CourseEmbedding;
import com.yeogido.backend.domain.course.embedding.repository.CourseEmbeddingDocumentRepository;
import com.yeogido.backend.domain.course.embedding.repository.CourseEmbeddingDocumentRepository.CourseDocument;
import com.yeogido.backend.domain.course.embedding.repository.CourseEmbeddingRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
public class CourseEmbeddingIndexService {

    private final CourseEmbeddingDocumentRepository documents;
    private final CourseEmbeddingRepository embeddings;
    private final CourseEmbeddingClient client;
    private final ObjectMapper mapper;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;
    private final int batchSize;
    private final int scanSize;
    private long lastScannedId;

    public CourseEmbeddingIndexService(CourseEmbeddingDocumentRepository documents,
            CourseEmbeddingRepository embeddings, CourseEmbeddingClient client, ObjectMapper mapper,
            PlatformTransactionManager transactionManager,
            @Value("${app.course-search.embedding.index-batch-size:20}") int batchSize,
            @Value("${app.course-search.embedding.index-scan-size:100}") int scanSize) {
        if (batchSize < 1 || batchSize > 100 || scanSize < batchSize || scanSize > 1000) {
            throw new IllegalArgumentException("Invalid course embedding indexing batch size");
        }
        this.documents = documents;
        this.embeddings = embeddings;
        this.client = client;
        this.mapper = mapper;
        this.batchSize = batchSize;
        this.scanSize = scanSize;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.writeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // Rotating scans revisit linked place/content updates without coupling their write paths to indexing.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public synchronized int reindexNextBatch() {
        if (!client.isEnabled()) return 0;
        List<CourseDocument> pending = readTransaction.execute(status -> nextChangedDocuments());
        if (pending.isEmpty()) return 0;

        // No database transaction is held while the provider embeds the batch.
        List<List<Double>> vectors = client.embedDocuments(pending.stream().map(CourseDocument::text).toList());
        if (vectors.size() != pending.size()) {
            throw new IllegalStateException("Embedding provider returned an unexpected batch size");
        }
        return writeTransaction.execute(status -> {
            Map<Long, CourseDocument> current = documents.findDocuments(pending.stream().map(CourseDocument::courseId).toList());
            int saved = 0;
            for (int index = 0; index < pending.size(); index++) {
                CourseDocument original = pending.get(index);
                CourseDocument latest = current.get(original.courseId());
                // Drop results superseded by an edit or delete while the external request was running.
                if (latest == null || !latest.sourceHash().equals(original.sourceHash())) continue;
                List<Double> vector = vectors.get(index);
                if (!validVector(vector)) throw new IllegalStateException("Invalid embedding vector");
                embeddings.save(new CourseEmbedding(original.courseId(), client.getModel(), client.getDimensions(),
                        original.sourceHash(), serialize(vector), LocalDateTime.now()));
                saved++;
            }
            return saved;
        });
    }

    public List<IndexedVector> findIndexedVectors() {
        if (!client.isEnabled()) return List.of();
        return readTransaction.execute(status -> {
            List<IndexedVector> result = new ArrayList<>();
            for (CourseEmbedding row : embeddings.findActiveVectors(client.getModel(), client.getDimensions())) {
                List<Double> vector = parseVector(row.getVectorJson());
                if (vector == null) {
                    log.warn("Invalid stored course embedding. courseId={}", row.getCourseId());
                    continue;
                }
                result.add(new IndexedVector(row.getCourseId(), vector));
            }
            return List.copyOf(result);
        });
    }

    private List<CourseDocument> nextChangedDocuments() {
        List<Long> ids = documents.findCourseIdsAfter(lastScannedId, scanSize);
        if (ids.isEmpty() && lastScannedId != 0) {
            lastScannedId = 0;
            ids = documents.findCourseIdsAfter(0, scanSize);
        }
        Map<Long, CourseDocument> source = documents.findDocuments(ids);
        Map<Long, CourseEmbedding> indexed = embeddings.findAllById(ids).stream()
                .collect(Collectors.toMap(CourseEmbedding::getCourseId, Function.identity()));
        List<CourseDocument> pending = new ArrayList<>();
        for (Long id : ids) {
            // Advance before calling the provider, so a repeatedly failing batch cannot starve later IDs.
            lastScannedId = id;
            CourseDocument document = source.get(id);
            if (document == null) continue;
            CourseEmbedding existing = indexed.get(id);
            if (existing == null || !existing.matches(client.getModel(), client.getDimensions(), document.sourceHash())
                    || parseVector(existing.getVectorJson()) == null) {
                pending.add(document);
                if (pending.size() == batchSize) break;
            }
        }
        return pending;
    }

    private List<Double> parseVector(String json) {
        try {
            JsonNode node = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(json);
            if (node == null || !node.isArray() || node.size() != client.getDimensions()) return null;
            List<Double> values = new ArrayList<>();
            for (JsonNode element : node) {
                if (!element.isNumber()) return null;
                values.add(element.doubleValue());
            }
            return validVector(values) ? List.copyOf(values) : null;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean validVector(List<Double> vector) {
        if (vector == null || vector.size() != client.getDimensions()) return false;
        double norm = 0;
        for (Double value : vector) {
            if (value == null || !Double.isFinite(value)) return false;
            norm = Math.hypot(norm, value);
        }
        return norm > 0 && Double.isFinite(norm);
    }

    private String serialize(List<Double> vector) {
        try {
            return mapper.writeValueAsString(vector);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize course embedding");
        }
    }

    public record IndexedVector(Long courseId, List<Double> vector) {
        public IndexedVector {
            vector = List.copyOf(vector);
        }
    }
}
