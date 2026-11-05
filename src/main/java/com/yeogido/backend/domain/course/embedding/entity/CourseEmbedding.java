package com.yeogido.backend.domain.course.embedding.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "course_embedding")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CourseEmbedding {

    @Id
    @Column(name = "course_id")
    private Long courseId;

    @Column(nullable = false, length = 128)
    private String model;

    @Column(nullable = false)
    private Integer dimensions;

    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;

    @Column(name = "vector_json", nullable = false, columnDefinition = "LONGTEXT")
    private String vectorJson;

    @Column(name = "indexed_at", nullable = false)
    private LocalDateTime indexedAt;

    public boolean matches(String model, int dimensions, String sourceHash) {
        return this.model.equals(model) && this.dimensions == dimensions && this.sourceHash.equals(sourceHash);
    }
}
