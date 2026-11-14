package com.yeogido.backend.domain.travel.service;

import com.yeogido.backend.domain.travel.entity.TravelRecordParticipationLock;
import com.yeogido.backend.domain.travel.repository.TravelRecordParticipationLockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class TravelRecordParticipationLockService {
    private final TravelRecordParticipationLockRepository repository;
    private final PlatformTransactionManager transactionManager;

    /** 다른 도메인 행을 읽거나 잠그기 전에 호출하고, 호출자 트랜잭션 종료까지 잠금을 유지합니다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(Long userId) {
        // 초기 행 생성만 별도 커밋합니다. 중복 생성 실패가 업무 트랜잭션을 rollback-only로 만들지 않습니다.
        TransactionTemplate initialization = new TransactionTemplate(transactionManager);
        initialization.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            initialization.executeWithoutResult(status -> {
                if (!repository.existsById(userId)) {
                    repository.saveAndFlush(new TravelRecordParticipationLock(userId));
                }
            });
        } catch (DataIntegrityViolationException concurrentInitialization) {
            // 동시에 최초 요청이 들어오면 PK 유니크 제약으로 하나만 생성됩니다.
            if (repository.findForUpdate(userId).isEmpty()) {
                throw concurrentInitialization;
            }
            return;
        }
        repository.findForUpdate(userId).orElseThrow();
    }
}
