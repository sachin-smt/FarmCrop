package com.farmcrop.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Enables created/updated timestamp auditing for entities using {@code AuditingEntityListener}. */
@Configuration
@EnableJpaAuditing
@EnableTransactionManagement
public class JpaConfig {
}
