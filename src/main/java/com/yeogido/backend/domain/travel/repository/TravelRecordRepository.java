package com.yeogido.backend.domain.travel.repository;

import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.travel.entity.TravelRecord;
import com.yeogido.backend.domain.travel.enums.FolderTheme;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelRecordRepository extends JpaRepository<TravelRecord, Long>, TravelRecordQueryRepository {

    /** 파일 이동 전에 버전을 선점하여 충돌한 요청의 임시 사진이 소모되지 않게 합니다. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update TravelRecord tr set tr.version = tr.version + 1
            where tr.id = :id and tr.version = :version
            """)
    int reserveVersion(@Param("id") Long id, @Param("version") Long version);

    /** 같은 트랜잭션에서 확보한 버전으로 앨범 정보를 저장하고 버전은 추가 증가시키지 않습니다. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update TravelRecord tr
            set tr.region = :region, tr.title = :title,
                tr.startDate = :startDate, tr.endDate = :endDate,
                tr.coverImageKey = :coverImageKey, tr.folderTheme = :folderTheme,
                tr.updatedAt = :updatedAt
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

    /** 전체 삭제·나가기를 공동 편집의 버전 선점과 같은 앨범 행에서 직렬화합니다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select tr from TravelRecord tr
            where tr.id = :id and exists (
                select 1 from User owner where owner.id = tr.user.id
                    and owner.status = com.yeogido.backend.domain.user.enums.UserStatus.ACTIVE
            )
            """)
    Optional<TravelRecord> findActiveDetailForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"user", "region"})
    @Query("""
            select tr from TravelRecord tr
            where tr.id = :id and tr.user.status = com.yeogido.backend.domain.user.enums.UserStatus.ACTIVE
            """)
    Optional<TravelRecord> findActiveDetailById(@Param("id") Long id);
}
