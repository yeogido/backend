package com.yeogido.backend.domain.travel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yeogido.backend.domain.travel.repository.TravelRecordParticipationLockRepository;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:participation-lock;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.connection-timeout=1000"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(TravelRecordParticipationLockService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TravelRecordParticipationLockIntegrationTest {
    @Autowired private TravelRecordParticipationLockService service;
    @Autowired private TravelRecordParticipationLockRepository repository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;

    @Test
    void firstAndRepeatedAcquisitionWorkWithOneConnection() throws Exception {
        assertThat(dataSource.unwrap(HikariDataSource.class).getMaximumPoolSize()).isEqualTo(1);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> service.acquire(70001L));
        tx.executeWithoutResult(status -> service.acquire(70001L));
        assertThat(repository.findById(70001L)).isPresent();
    }

    @Test
    void failedBusinessTransactionRollsBackNewLockRowAndAllowsRetry() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            service.acquire(70002L);
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(repository.existsById(70002L)).isFalse();
        tx.executeWithoutResult(status -> service.acquire(70002L));
        assertThat(repository.existsById(70002L)).isTrue();
    }
}
