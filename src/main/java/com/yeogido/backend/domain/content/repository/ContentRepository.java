package com.yeogido.backend.domain.content.repository;

import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.enums.ContentSource;
import com.yeogido.backend.domain.content.enums.ContentPublicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Collection;
import java.util.Optional;

@Repository
public interface ContentRepository extends JpaRepository<Content,Long> {

    List<Content> findTop5ByPublicationStatusAndEndDateGreaterThanEqualOrderByEndDateAsc(
            ContentPublicationStatus publicationStatus,
            LocalDate today
    );

    List<Content> findAllBySourceAndPublicationStatusOrderByCreatedAtDesc(
            ContentSource source,
            ContentPublicationStatus publicationStatus
    );

    List<Content> findAllByIdInAndPublicationStatus(
            Collection<Long> ids,
            ContentPublicationStatus publicationStatus
    );

    boolean existsBySourceAndExternalContentId(
            ContentSource source,
            String externalContentId
    );

    Optional<Content> findBySourceAndExternalContentId(
            ContentSource source,
            String externalContentId
    );

}
