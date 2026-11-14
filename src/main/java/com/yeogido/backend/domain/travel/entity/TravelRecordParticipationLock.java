package com.yeogido.backend.domain.travel.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자 행과 독립된 잠금으로 초대 응답과 탈퇴의 참여 관계 변경을 직렬화합니다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "travel_record_participation_lock")
public class TravelRecordParticipationLock {
    @Id
    private Long userId;

    public TravelRecordParticipationLock(Long userId) {
        this.userId = userId;
    }
}
