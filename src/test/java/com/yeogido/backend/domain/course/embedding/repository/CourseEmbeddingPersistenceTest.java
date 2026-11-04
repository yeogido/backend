package com.yeogido.backend.domain.course.embedding.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.enums.ContentSource;
import com.yeogido.backend.domain.course.embedding.entity.CourseEmbedding;
import com.yeogido.backend.domain.course.entity.Course;
import com.yeogido.backend.domain.course.entity.CourseItem;
import com.yeogido.backend.domain.course.enums.CourseItemType;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.global.config.JpaConfig;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@Import({JpaConfig.class, CourseEmbeddingDocumentRepository.class})
@ActiveProfiles("test")
class CourseEmbeddingPersistenceTest {
    @Autowired TestEntityManager entityManager;
    @Autowired CourseEmbeddingDocumentRepository documents;
    @Autowired CourseEmbeddingRepository embeddings;

    @Test
    void buildsFromPlaceAndContentAndDetectsSharedOverviewChanges() {
        Region region = entityManager.persist(Region.builder().name("부산").fullName("부산광역시").type(RegionType.REGION).build());
        Course course = entityManager.persist(Course.builder().region(region).title("빗소리 여행").description("둘이 즐기는 실내 코스")
                .courseType(CourseType.OFFICIAL).build());
        Place place = entityManager.persist(Place.builder().region(region).name("서점").overview("차분한 독서 공간")
                .externalPlaceId("embedding-place").source(PlaceSource.KAKAO)
                .latitude(BigDecimal.ZERO).longitude(BigDecimal.ZERO).build());
        Content content = entityManager.persist(Content.builder().place(place).source(ContentSource.ADMIN)
                .title("낭독회").description("작가와 만나는 시간").build());
        entityManager.persist(CourseItem.builder().course(course).place(place).itemType(CourseItemType.PLACE).orderNo(1).build());
        entityManager.persist(CourseItem.builder().course(course).content(content).itemType(CourseItemType.CONTENT).orderNo(2).build());
        entityManager.flush();
        var before = documents.findDocuments(List.of(course.getId())).get(course.getId());
        assertThat(before.text()).contains("빗소리 여행", "둘이 즐기는 실내 코스", "부산", "서점", "차분한 독서 공간", "낭독회", "작가와 만나는 시간");
        ReflectionTestUtils.setField(place, "overview", "비 오는 날 쉬기 좋은 공간");
        entityManager.flush();
        assertThat(documents.findDocuments(List.of(course.getId())).get(course.getId()).sourceHash()).isNotEqualTo(before.sourceHash());

        embeddings.saveAndFlush(new CourseEmbedding(course.getId(), "model", 2, before.sourceHash(), "[1,0]", LocalDateTime.now()));
        assertThat(embeddings.findActiveVectors("model", 2)).hasSize(1);
        assertThat(embeddings.findActiveVectors("other-model", 2)).isEmpty();
        assertThat(embeddings.findActiveVectors("model", 3)).isEmpty();
        ReflectionTestUtils.setField(course, "deletedAt", LocalDateTime.now());
        entityManager.flush();
        assertThat(embeddings.findActiveVectors("model", 2)).isEmpty();
        assertThat(documents.findCourseIdsAfter(0L, 10)).isEmpty();
        assertThat(documents.findDocuments(List.of(course.getId()))).isEmpty();
    }
}
