package com.yeogido.backend.domain.hashtag.repository;

import com.yeogido.backend.domain.hashtag.entity.Hashtag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface HashtagRepository extends JpaRepository<Hashtag, Long> {

    @Query("""
        SELECT h
        FROM Hashtag h
        ORDER BY
            CASE WHEN h.displayOrder = 0 THEN 1 ELSE 0 END ASC,
            h.displayOrder ASC,
            h.id ASC
        """)
    List<Hashtag> findAllByDisplayOrder();
}