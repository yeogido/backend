package com.yeogido.backend.domain.content.repository;

import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.entity.ContentHashtag;
import com.yeogido.backend.domain.course.entity.CourseHashtag;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ContentHashtagRepository extends JpaRepository<ContentHashtag,Long> {

    @Modifying
    @Transactional
    @Query("delete from ContentHashtag ch where ch.content.id = :contentId")
    void deleteByContentId(@Param("contentId") Long contentId);
  
    List<ContentHashtag> findByContent(Content content);

    List<ContentHashtag> findByContentIdIn(List<Long> courseIds);
    void deleteByContent(Content content);
}