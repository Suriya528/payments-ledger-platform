package com.suriya.payments.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for integration tests. Three modes:
 *
 * <ul>
 *   <li><b>Docker / CI</b> — spins a singleton Postgres via Testcontainers when Docker is active.
 *   <li><b>External DB</b> — if {@code EXTERNAL_DB_URL} is set, connects to that Postgres instead.
 *   <li><b>Fallback H2</b> — gracefully falls back to H2 in PostgreSQL compatibility mode when Docker
 *       is not running locally.
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

  private static final Logger log = LoggerFactory.getLogger(AbstractIntegrationTest.class);

  private static final String EXTERNAL_DB_URL = System.getenv("EXTERNAL_DB_URL");
  private static final boolean USE_EXTERNAL_DB =
      EXTERNAL_DB_URL != null && !EXTERNAL_DB_URL.isBlank();

  private static PostgreSQLContainer<?> postgres;
  private static boolean dockerAvailable = false;

  static {
    if (!USE_EXTERNAL_DB) {
      dockerAvailable = checkDockerAvailability();
      if (dockerAvailable) {
        try {
          postgres = new PostgreSQLContainer<>("postgres:16-alpine");
          postgres.start();
          log.info("Started Testcontainers PostgreSQL on {}", postgres.getJdbcUrl());
        } catch (Exception e) {
          log.warn("Failed to start Testcontainers PostgreSQL, falling back to in-memory H2: {}", e.getMessage());
          dockerAvailable = false;
        }
      } else {
        log.info("Docker is not available; falling back to H2 PostgreSQL mode for integration tests.");
      }
    }
  }

  private static boolean checkDockerAvailability() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (Throwable t) {
      return false;
    }
  }

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    if (USE_EXTERNAL_DB) {
      registry.add("spring.datasource.url", () -> EXTERNAL_DB_URL);
      registry.add(
          "spring.datasource.username",
          () -> System.getenv().getOrDefault("EXTERNAL_DB_USER", "payments"));
      registry.add(
          "spring.datasource.password",
          () -> System.getenv().getOrDefault("EXTERNAL_DB_PASSWORD", "payments"));
    } else if (dockerAvailable && postgres != null && postgres.isRunning()) {
      registry.add("spring.datasource.url", postgres::getJdbcUrl);
      registry.add("spring.datasource.username", postgres::getUsername);
      registry.add("spring.datasource.password", postgres::getPassword);
    } else {
      registry.add(
          "spring.datasource.url",
          () -> "jdbc:h2:mem:testpayments;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
      registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
      registry.add("spring.datasource.username", () -> "sa");
      registry.add("spring.datasource.password", () -> "");
      registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
      registry.add("spring.flyway.enabled", () -> "false");
    }
    registry.add("spring.datasource.hikari.maximum-pool-size", () -> "10");
  }
}

