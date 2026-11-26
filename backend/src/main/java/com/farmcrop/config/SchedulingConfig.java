package com.farmcrop.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Background housekeeping (token purge, later reservation expiry). Disabled in tests. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "farmcrop.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
