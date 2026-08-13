package com.yeogido.backend.domain.content.repository;

import com.yeogido.backend.domain.content.entity.ContentExternalLink;
import com.yeogido.backend.domain.content.enums.ContentLinkSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContentExternalLinkRepository
        extends JpaRepository<ContentExternalLink, Long> {

    List<ContentExternalLink> findAllByContentIdOrderByDisplayOrderAsc(Long contentId);

    List<ContentExternalLink> findAllByContentIdAndSource(
            Long contentId,
            ContentLinkSource source
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ContentExternalLink link where link.content.id = :contentId")
    void deleteAllByContentId(@Param("contentId") Long contentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ContentExternalLink link where link.content.id = :contentId and link.source = :source")
    void deleteAllByContentIdAndSource(
            @Param("contentId") Long contentId,
            @Param("source") ContentLinkSource source
    );
}
