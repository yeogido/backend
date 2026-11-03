package com.yeogido.backend.domain.place.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("mysql-migration")
class PlaceOverviewRetryMigrationTest {
  private Connection server;
  private String schema;
  private String url;
  private String username;
  private String password;

  @BeforeEach
  void prepareSchema() throws SQLException {
    String serverUrl = System.getenv("MYSQL_MIGRATION_TEST_URL");
    assertThat(serverUrl).as("MYSQL_MIGRATION_TEST_URL is required").isNotBlank();
    username = System.getenv().getOrDefault("MYSQL_MIGRATION_TEST_USER", "root");
    password = System.getenv().getOrDefault("MYSQL_MIGRATION_TEST_PASSWORD", "");
    schema = "place_overview_retry_" + UUID.randomUUID().toString().replace("-", "");
    server = DriverManager.getConnection(serverUrl, username, password);
    try (var statement = server.createStatement()) {
      statement.execute("CREATE DATABASE " + schema);
    }
    url = "jdbc:mysql://" + server.getMetaData().getURL()
        .substring("jdbc:mysql://".length()).split("/", 2)[0] + "/" + schema;
    Flyway.configure().dataSource(url, username, password).target("41").load().migrate();
  }

  @AfterEach
  void removeTestSchema() throws SQLException {
    if (server != null) {
      try (var connection = server; var statement = connection.createStatement()) {
        statement.execute("DROP DATABASE IF EXISTS " + schema);
      }
    }
  }

  @Test
  void preservesExistingOverviewsAndAttemptTimesAndBackfillsAttemptCounts() throws SQLException {
    try (var connection = DriverManager.getConnection(url, username, password);
         var statement = connection.createStatement()) {
      statement.execute("""
          INSERT INTO region (id, name, type, created_at, updated_at)
          VALUES (1, '서울', 'REGION', NOW(6), NOW(6))
          """);
      for (var status : new String[]{"PENDING", "PROCESSING", "COMPLETED", "NO_OVERVIEW", "FAILED"}) {
        try (var insert = connection.prepareStatement("""
            INSERT INTO place (region_id, external_place_id, source, name, latitude, longitude,
                overview, overview_status, overview_attempted_at, created_at, updated_at)
            VALUES (1, ?, 'KAKAO', ?, 37, 127, ?, ?, '2026-11-02 12:00:00', NOW(6), NOW(6))
            """)) {
          insert.setString(1, status);
          insert.setString(2, status);
          insert.setString(3, "COMPLETED".equals(status) ? "기존 설명" : null);
          insert.setString(4, status);
          insert.executeUpdate();
        }
      }
      Flyway.configure().dataSource(url, username, password).load().migrate();
      try (var rows = statement.executeQuery("""
          SELECT overview_status, overview, overview_attempt_count, overview_attempted_at
          FROM place
          """)) {
        int count = 0;
        while (rows.next()) {
          count++;
          String status = rows.getString("overview_status");
          assertThat(rows.getInt("overview_attempt_count")).isEqualTo("PENDING".equals(status) ? 0 : 1);
          assertThat(rows.getString("overview")).isEqualTo("COMPLETED".equals(status) ? "기존 설명" : null);
          assertThat(rows.getTimestamp("overview_attempted_at").toLocalDateTime())
              .isEqualTo(java.time.LocalDateTime.of(2026, 11, 2, 12, 0));
        }
        assertThat(count).isEqualTo(5);
      }
    }
  }
}
