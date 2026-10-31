package com.yeogido.backend.domain.travel.repository;

import com.yeogido.backend.domain.travel.entity.TravelRecordMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelRecordMemberRepository extends JpaRepository<TravelRecordMember, Long> {

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
}
