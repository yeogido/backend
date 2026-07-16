package com.yeogido.backend.domain.content.repository;

import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.entity.ContentHashtag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentHashtagRepository extends JpaRepository<ContentHashtag,Long> {
    List<ContentHashtag> findByContent(Content content);
}
