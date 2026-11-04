package com.yeogido.backend.domain.course.embedding;

import com.yeogido.backend.domain.course.client.CourseEmbeddingClient;
import com.yeogido.backend.domain.course.dto.CourseSearchDTO.Interpretation;
import com.yeogido.backend.domain.course.embedding.service.CourseEmbeddingIndexService;
import com.yeogido.backend.domain.course.exception.CourseErrorCode;
import com.yeogido.backend.domain.course.repository.CourseSemanticSearchSessionRepository;
import com.yeogido.backend.global.exception.GeneralException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CourseSemanticSearchService {

    private final CourseEmbeddingClient client;
    private final CourseEmbeddingIndexService index;
    private final CourseSemanticSearchSessionRepository sessions;
    private final double minimumSimilarity;

    public CourseSemanticSearchService(CourseEmbeddingClient client, CourseEmbeddingIndexService index,
            CourseSemanticSearchSessionRepository sessions,
            @Value("${app.course-search.embedding.minimum-similarity:0.72}") double minimumSimilarity) {
        if (!Double.isFinite(minimumSimilarity) || minimumSimilarity < -1 || minimumSimilarity > 1) {
            throw new GeneralException(CourseErrorCode.INVALID_COURSE_SEARCH_CONFIG);
        }
        this.client = client;
        this.index = index;
        this.sessions = sessions;
        this.minimumSimilarity = minimumSimilarity;
    }

    public boolean isEnabled() {
        return client.isEnabled();
    }

    public SemanticCandidates search(Interpretation interpretation) {
        String query = String.join(" ", interpretation.terms());
        List<Double> queryVector = client.embedQuery(query);
        List<Long> ids = index.findIndexedVectors().stream()
                .filter(document -> cosine(queryVector, document.vector()) >= minimumSimilarity)
                .map(CourseEmbeddingIndexService.IndexedVector::courseId)
                .distinct().toList();
        return new SemanticCandidates(sessions.save(query, ids), ids);
    }

    public List<Long> restore(Interpretation interpretation) {
        return sessions.find(interpretation.semanticSessionId(), String.join(" ", interpretation.terms()));
    }

    static double cosine(List<Double> left, List<Double> right) {
        if (left.isEmpty() || left.size() != right.size()) {
            throw new GeneralException(CourseErrorCode.INVALID_COURSE_SEARCH_RESPONSE);
        }
        double product = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.size(); i++) {
            Double a = left.get(i);
            Double b = right.get(i);
            if (a == null || b == null || !Double.isFinite(a) || !Double.isFinite(b)) {
                throw new GeneralException(CourseErrorCode.INVALID_COURSE_SEARCH_RESPONSE);
            }
            leftNorm = Math.hypot(leftNorm, a);
            rightNorm = Math.hypot(rightNorm, b);
        }
        if (leftNorm == 0 || rightNorm == 0 || !Double.isFinite(leftNorm) || !Double.isFinite(rightNorm)) {
            throw new GeneralException(CourseErrorCode.INVALID_COURSE_SEARCH_RESPONSE);
        }
        for (int i = 0; i < left.size(); i++) {
            product += (left.get(i) / leftNorm) * (right.get(i) / rightNorm);
        }
        double similarity = product;
        if (!Double.isFinite(similarity)) {
            throw new GeneralException(CourseErrorCode.INVALID_COURSE_SEARCH_RESPONSE);
        }
        return Math.max(-1, Math.min(1, similarity));
    }

    public record SemanticCandidates(String sessionId, List<Long> courseIds) {
        public SemanticCandidates {
            courseIds = List.copyOf(courseIds);
        }
    }
}
