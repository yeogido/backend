package com.yeogido.backend.domain.course.embedding.repository;

import com.yeogido.backend.domain.course.embedding.entity.CourseEmbedding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseEmbeddingRepository extends JpaRepository<CourseEmbedding, Long> {

    @Query("""
            select embedding from CourseEmbedding embedding, Course course
            where embedding.courseId = course.id
              and course.deletedAt is null
              and embedding.model = :model
              and embedding.dimensions = :dimensions
            order by embedding.courseId
            """)
    List<CourseEmbedding> findActiveVectors(@Param("model") String model, @Param("dimensions") int dimensions);
}
