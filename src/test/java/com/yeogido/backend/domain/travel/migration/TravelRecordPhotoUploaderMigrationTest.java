package com.yeogido.backend.domain.travel.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** V52 상태의 사진을 보존하며 업로더 FK와 사진 없는 폴더를 지원하는지 실제 MySQL에서 검증합니다. */
@Tag("mysql-migration")
class TravelRecordPhotoUploaderMigrationTest {
  @Test
  void preservesUnknownUploaderAndAllowsEmptyCoverWhileEnforcingUploaderForeignKey() throws SQLException {
    String serverUrl = System.getenv("MYSQL_MIGRATION_TEST_URL");
    assertThat(serverUrl).as("Set MYSQL_MIGRATION_TEST_URL to a disposable MySQL server URL").isNotBlank();
    String user = System.getenv().getOrDefault("MYSQL_MIGRATION_TEST_USER", "root");
    String password = System.getenv().getOrDefault("MYSQL_MIGRATION_TEST_PASSWORD", "");
    String schema = "travel_photo_migration_" + UUID.randomUUID().toString().replace("-", "");
    try (Connection server = DriverManager.getConnection(serverUrl, user, password)) {
      try {
        try (var statement = server.createStatement()) {
          statement.execute("CREATE DATABASE " + schema);
        }
        String databaseUrl = "jdbc:mysql://" + server.getMetaData().getURL()
            .substring("jdbc:mysql://".length()).split("/", 2)[0] + "/" + schema;
        try (Connection database = DriverManager.getConnection(databaseUrl, user, password);
             var statement = database.createStatement()) {
          statement.execute("CREATE TABLE `user` (id BIGINT PRIMARY KEY)");
          statement.execute("CREATE TABLE travel_record (id BIGINT PRIMARY KEY, cover_image_key VARCHAR(255) NOT NULL)");
          statement.execute("CREATE TABLE travel_record_photo (id BIGINT PRIMARY KEY, travel_record_id BIGINT NOT NULL, image_key VARCHAR(255) NOT NULL, image_order INT NOT NULL)");
          statement.execute("INSERT INTO `user` VALUES (1)");
          statement.execute("INSERT INTO travel_record VALUES (1, 'travel-records/legacy.jpg')");
          statement.execute("INSERT INTO travel_record_photo VALUES (1, 1, 'travel-records/legacy.jpg', 1)");
          Flyway.configure().dataSource(databaseUrl, user, password).baselineOnMigrate(true)
              .baselineVersion("52").target("54").load().migrate();
          try (var result = statement.executeQuery("SELECT image_key, image_order, uploader_id FROM travel_record_photo")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("travel-records/legacy.jpg");
            assertThat(result.getInt(2)).isEqualTo(1);
            assertThat(result.getObject(3)).isNull();
          }
          statement.execute("UPDATE travel_record SET cover_image_key = NULL");
          statement.execute("UPDATE travel_record_photo SET uploader_id = 1");
          assertThatThrownBy(() -> statement.execute("UPDATE travel_record_photo SET uploader_id = 999"))
              .isInstanceOf(SQLException.class).hasMessageContaining("fk_travel_record_photo_uploader");
        }
      } finally {
        try (var statement = server.createStatement()) {
          statement.execute("DROP DATABASE IF EXISTS " + schema);
        }
      }
    }
  }
}
