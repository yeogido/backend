package com.yeogido.backend.domain.content.repository;

import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.enums.ContentSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContentRepository extends JpaRepository<Content,Long> {

    List<Content> findTop5ByEndDateGreaterThanEqualOrderByEndDateAsc(LocalDate today);

    boolean existsBySourceAndExternalContentId(
            ContentSource source,
            String externalContentId
    );

    Optional<Content> findBySourceAndExternalContentId(
            ContentSource source,
            String externalContentId
    );

}
