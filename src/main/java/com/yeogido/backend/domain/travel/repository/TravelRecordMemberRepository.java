package com.yeogido.backend.domain.travel.repository;

import com.yeogido.backend.domain.travel.entity.TravelRecordMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelRecordMemberRepository extends JpaRepository<TravelRecordMember, Long> {

  @Query("""
      select member from TravelRecordMember member join fetch member.user
      where member.travelRecord.id in :recordIds
        and member.user.status = com.yeogido.backend.domain.user.enums.UserStatus.ACTIVE
        and member.user.id <> member.travelRecord.user.id
      order by member.id asc
      """)
  List<TravelRecordMember> findCurrentMembers(@Param("recordIds") List<Long> recordIds);
}
