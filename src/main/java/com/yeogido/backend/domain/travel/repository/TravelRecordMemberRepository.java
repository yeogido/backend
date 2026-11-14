package com.yeogido.backend.domain.travel.repository;

import com.yeogido.backend.domain.travel.entity.TravelRecordMember;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelRecordMemberRepository extends JpaRepository<TravelRecordMember, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from TravelRecordMember m where m.travelRecord.id = :recordId and m.user.id = :userId")
  Optional<TravelRecordMember> findCurrentForUpdate(@Param("recordId") Long recordId, @Param("userId") Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from TravelRecordMember m where m.travelRecord.id = :recordId")
  List<TravelRecordMember> findMembersForUpdate(@Param("recordId") Long recordId);

  @Query("select m.travelRecord.id from TravelRecordMember m where m.user.id = :userId order by m.travelRecord.id")
  List<Long> findJoinedRecordIds(@Param("userId") Long userId);

  /** 로그인 사용자의 현재 참여 관계만 제거하고 다른 참여자·콘텐츠는 유지합니다. */
  @Modifying(flushAutomatically = true)
  @Query("""
      delete from TravelRecordMember member
      where member.travelRecord.id = :recordId and member.user.id = :userId
      """)
  int deleteCurrentMembership(@Param("recordId") Long recordId, @Param("userId") Long userId);

  @Query("""
      select member from TravelRecordMember member join fetch member.user
      where member.travelRecord.id in :recordIds
        and member.user.status = com.yeogido.backend.domain.user.enums.UserStatus.ACTIVE
        and member.user.id <> member.travelRecord.user.id
      order by member.id asc
      """)
  List<TravelRecordMember> findCurrentMembers(@Param("recordIds") List<Long> recordIds);

  /**
   * 두 사용자가 각각 생성자 또는 참여자로 함께한 공유 앨범을 조회합니다.
   * 생성자는 참여자 테이블에 없으므로 생성자 ID를 함께 돌려줍니다.
   * remainingMemberCount는 생성자와 otherUserId를 뺀 활성 참여자 수이므로, 조회 중 상대가 탈퇴해도 값이 달라지지 않습니다.
   */
  @Query("""
      select record.id as travelRecordId, owner.id as ownerId,
          (select count(member) from TravelRecordMember member
              where member.travelRecord = record
                and member.user.status = com.yeogido.backend.domain.user.enums.UserStatus.ACTIVE
                and member.user.id <> owner.id
                and member.user.id <> :otherUserId) as remainingMemberCount
      from TravelRecord record join record.user owner
      where record.visibility = com.yeogido.backend.domain.travel.enums.TravelRecordVisibility.FRIENDS
        and owner.status = com.yeogido.backend.domain.user.enums.UserStatus.ACTIVE
        and (owner.id = :userId or exists (select 1 from TravelRecordMember member
            where member.travelRecord = record and member.user.id = :userId))
        and (owner.id = :otherUserId or exists (select 1 from TravelRecordMember member
            where member.travelRecord = record and member.user.id = :otherUserId))
      order by record.id asc
      """)
  List<SharedAlbum> findSharedAlbums(@Param("userId") Long userId, @Param("otherUserId") Long otherUserId);

  interface SharedAlbum {
    Long getTravelRecordId();

    Long getOwnerId();

    Long getRemainingMemberCount();
  }
}
