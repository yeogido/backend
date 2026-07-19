package com.yeogido.backend.domain.content.repository;

import com.yeogido.backend.domain.content.entity.ContentHashtag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentHashtagRepository extends JpaRepository<ContentHashtag,Long> {
}
