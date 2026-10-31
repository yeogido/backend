package com.yeogido.backend.domain.travel.repository;

import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.travel.entity.TravelRecord;
import com.yeogido.backend.domain.travel.enums.FolderTheme;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelRecordRepository extends JpaRepository<TravelRecord, Long>, TravelRecordQueryRepository {

    /** 사진·스티커만 바뀌어도 버전을 선점하여 동일 버전의 동시 저장을 방지합니다. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update TravelRecord tr
            set tr.region = :region, tr.title = :title,
                tr.startDate = :startDate, tr.endDate = :endDate,
                tr.coverImageKey = :coverImageKey, tr.folderTheme = :folderTheme,
                tr.version = tr.version + 1, tr.updatedAt = :updatedAt
            where tr.id = :id and tr.version = :version
            """)
    int updateIfVersionMatches(
            @Param("id") Long id, @Param("version") Long version,
            @Param("region") Region region, @Param("title") String title,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
            @Param("coverImageKey") String coverImageKey, @Param("folderTheme") FolderTheme folderTheme,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from TravelRecord tr
            where tr.user.id = :userId
            """)
    int deleteAllByUserId(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"user", "region"})
    @Query("""
            select tr from TravelRecord tr
            where tr.id = :id and tr.user.status = com.yeogido.backend.domain.user.enums.UserStatus.ACTIVE
            """)
    Optional<TravelRecord> findActiveDetailById(@Param("id") Long id);
}
