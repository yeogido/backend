package com.yeogido.backend.domain.travel.repository;

import com.yeogido.backend.domain.travel.entity.TravelRecordMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelRecordMemberRepository extends JpaRepository<TravelRecordMember, Long> {
}
