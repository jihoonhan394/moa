package com.moara.moa.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @Scheduled 활성화(기동 순서 cron 스케줄러 등). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
