package com.yeogido.backend.domain.travel.service;

import com.yeogido.backend.domain.travel.repository.TravelRecordParticipationLockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TravelRecordParticipationLockService {
    private final TravelRecordParticipationLockRepository repository;

    /** 다른 도메인 행을 읽거나 잠그기 전에 호출하고, 호출자 트랜잭션 종료까지 잠금을 유지합니다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(Long userId) {
        // 같은 연결에서 최초 생성과 기존 행 잠금을 처리합니다. 일반 SELECT로 RR 스냅샷을 먼저 만들지 않습니다.
        repository.ensureLockRow(userId);
        repository.findForUpdate(userId).orElseThrow();
    }
}
