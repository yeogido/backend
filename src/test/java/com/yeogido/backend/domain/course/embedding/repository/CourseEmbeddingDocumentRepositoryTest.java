package com.yeogido.backend.domain.course.embedding.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CourseEmbeddingDocumentRepositoryTest {
    @Test
    void balancesKoreanAndEmojiSectionsWithinUtf8BudgetAndSamplesLastPlace() {
        List<String> sections = new ArrayList<>(List.of("코스: 데이트", "설명: " + "긴설명😀".repeat(1000), "지역: 부산"));
        for (int index = 0; index < 30; index++) sections.add("방문: 장소" + index + " 조용한 실내😀".repeat(20));
        var document = CourseEmbeddingDocumentRepository.buildDocument(1L, sections);
        assertThat(document.text().getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(1800);
        assertThat(document.text()).contains("코스: 데이트", "지역: 부산", "방문: 장소0", "방문: 장소29");
        assertThat(new String(document.text().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)).isEqualTo(document.text());
        assertThat(document.sourceHash()).isEqualTo(CourseEmbeddingDocumentRepository.buildDocument(1L, sections).sourceHash());
    }

    @Test
    void hashesChangesBeyondTheVisibleBudgetAndDeduplicatesRepeatedVisits() {
        List<String> sections = List.of("코스", "설명: " + "가".repeat(2000), "지역", "방문: 같은 장소", "방문: 같은 장소");
        var original = CourseEmbeddingDocumentRepository.buildDocument(1L, sections);
        var changed = CourseEmbeddingDocumentRepository.buildDocument(1L,
                List.of(sections.get(0), sections.get(1) + "변경", sections.get(2), sections.get(3), sections.get(4)));
        assertThat(original.text()).isEqualTo(changed.text());
        assertThat(original.sourceHash()).isNotEqualTo(changed.sourceHash());
        assertThat(original.text().lines().filter(line -> line.equals("방문: 같은 장소"))).hasSize(1);
    }
}
