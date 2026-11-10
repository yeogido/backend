package com.yeogido.backend.domain.place.migration;

import com.yeogido.backend.domain.place.enums.PlaceOverviewStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceOverviewRetryPolicyMigrationTest {

  @Test
  void addsSchedulingAndSharedGateWithoutResettingLegacyFailures() {
    var datasource = new DriverManagerDataSource("jdbc:h2:mem:overview_migration_"
        + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    var jdbc = new JdbcTemplate(datasource);
    try {
      jdbc.execute("""
          CREATE TABLE place (
            id BIGINT PRIMARY KEY, overview_status VARCHAR(20), overview TEXT,
            overview_attempt_count INT, overview_attempted_at DATETIME(6)
          )
          """);
      jdbc.update("""
          INSERT INTO place VALUES (1, 'FAILED', NULL, 2, '2026-11-06 10:29:00'),
              (2, 'COMPLETED', '기존 설명', 1, '2026-11-06 10:29:00')
          """);
      new ResourceDatabasePopulator(
          new ClassPathResource("db/migration/V46__add_place_overview_retry_policy.sql"),
          new ClassPathResource("db/migration/V47__add_overview_external_api_gate.sql"),
          new ClassPathResource("db/migration/V49__track_overview_api_wait.sql"))
          .execute(datasource);

      assertThat(jdbc.queryForObject("SELECT overview_status FROM place WHERE id=1", String.class))
          .isEqualTo("FAILED");
      assertThat(jdbc.queryForObject("SELECT overview_attempt_count FROM place WHERE id=1", Integer.class))
          .isEqualTo(2);
      assertThat(jdbc.queryForObject("SELECT overview FROM place WHERE id=2", String.class))
          .isEqualTo("기존 설명");
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM place WHERE overview_next_retry_at IS NULL",
          Integer.class)).isEqualTo(2);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM place WHERE overview_blocked_by_operation IS NULL",
          Integer.class)).isEqualTo(2);
      jdbc.update("UPDATE place SET overview_blocked_by_operation=? WHERE id=1", "gemini:generateContent");
      assertThat(jdbc.queryForObject("SELECT overview_blocked_by_operation FROM place WHERE id=1", String.class))
          .isEqualTo("gemini:generateContent");
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM place_overview_api_operation WHERE manual_release_required = FALSE",
          Integer.class)).isEqualTo(6);
      assertThat(jdbc.queryForList("SELECT id FROM place_overview_api_operation", String.class))
          .containsExactlyInAnyOrder("tour:/searchKeyword2", "tour:/detailCommon2", "tour:/ldongCode2",
              "daum:searchBlog", "daum:searchWeb", "gemini:generateContent");
    } finally {
      jdbc.execute("DROP ALL OBJECTS");
    }
  }

  @Test
  void resetsOnlyFailedPlacesWithoutOverviewForOneTimeRecovery() {
    var datasource = new DriverManagerDataSource("jdbc:h2:mem:overview_recovery_"
        + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    var jdbc = new JdbcTemplate(datasource);
    var previousTime = LocalDateTime.of(2026, 11, 6, 10, 29);
    try {
      jdbc.execute("""
          CREATE TABLE place (
            id BIGINT PRIMARY KEY, overview_status VARCHAR(20), overview TEXT,
            overview_attempt_count INT, overview_attempted_at DATETIME(6),
            overview_next_retry_at DATETIME(6), overview_source VARCHAR(30), updated_at DATETIME(6)
          )
          """);
      long id = 0;
      for (var status : PlaceOverviewStatus.values()) {
        for (String overview : new String[] {null, "", "   ", "기존 설명"}) {
          jdbc.update("INSERT INTO place VALUES (?, ?, ?, 2, ?, ?, 'TOUR_API', ?)",
              ++id, status.name(), overview, previousTime, previousTime, previousTime);
        }
      }

      new ResourceDatabasePopulator(new ClassPathResource(
          "db/migration/V48__reset_failed_place_overviews.sql")).execute(datasource);

      id = 0;
      for (var status : PlaceOverviewStatus.values()) {
        for (String overview : new String[] {null, "", "   ", "기존 설명"}) {
          var row = jdbc.queryForMap("SELECT * FROM place WHERE id = ?", ++id);
          boolean reset = status == PlaceOverviewStatus.FAILED
              && (overview == null || overview.isBlank());
          assertThat(row.get("overview_status")).isEqualTo(reset ? "PENDING" : status.name());
          assertThat(row.get("overview")).isEqualTo(overview);
          assertThat(row.get("overview_attempt_count")).isEqualTo(reset ? 0 : 2);
          assertThat(jdbc.queryForObject("SELECT overview_attempted_at FROM place WHERE id = ?",
              LocalDateTime.class, id)).isEqualTo(reset ? null : previousTime);
          assertThat(jdbc.queryForObject("SELECT overview_next_retry_at FROM place WHERE id = ?",
              LocalDateTime.class, id)).isEqualTo(reset ? null : previousTime);
          assertThat(row.get("overview_source")).isEqualTo(reset ? null : "TOUR_API");
          var updatedAt = jdbc.queryForObject("SELECT updated_at FROM place WHERE id = ?",
              LocalDateTime.class, id);
          if (reset) {
            assertThat(updatedAt).isAfter(previousTime);
          } else {
            assertThat(updatedAt).isEqualTo(previousTime);
          }
        }
      }
    } finally {
      jdbc.execute("DROP ALL OBJECTS");
    }
  }

}
