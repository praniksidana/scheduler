CREATE DATABASE IF NOT EXISTS `schedules`;
USE `schedules`;

-- Drop existing tables if they exist (for clean setup)
DROP VIEW IF EXISTS `job_execution_stats`;
DROP TABLE IF EXISTS `scheduled_jobs`;
DROP TABLE IF EXISTS `job_executions`;
DROP TABLE IF EXISTS `jobs`;

-- Jobs table: stores job definitions with CRON schedules
CREATE TABLE `jobs` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `name` VARCHAR(255),
  `type` VARCHAR(20) NOT NULL COMMENT 'JobType: ATLEAST_ONCE or ATMOST_ONCE',
  `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT 'Job status: active or paused',
  `schedule` VARCHAR(100) NOT NULL COMMENT '6-field CRON expression with seconds',
  `api_endpoint` VARCHAR(2048) NOT NULL COMMENT 'HTTP endpoint URL for POST requests',
  `payload` JSON NULL,
  `metadata` JSON NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Indexes for faster job lookups
CREATE INDEX `idx_jobs_status` ON `jobs`(`status`);
CREATE INDEX `idx_jobs_type` ON `jobs`(`type`);
CREATE INDEX `idx_jobs_schedule` ON `jobs`(`schedule`(50));

-- Job executions table: tracks all job execution history
CREATE TABLE `job_executions` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `job_id` BIGINT NOT NULL,
  `run_at` TIMESTAMP NOT NULL COMMENT 'Timestamp when job execution started',
  `http_status` INT NULL COMMENT 'HTTP status code from API response',
  `time_taken_ms` BIGINT NULL COMMENT 'Time taken in milliseconds',
  `error` VARCHAR(2048) NULL COMMENT 'Error message if execution failed',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`job_id`) REFERENCES `jobs`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Indexes for faster execution queries
CREATE INDEX `idx_job_id` ON `job_executions`(`job_id`);
CREATE INDEX `idx_job_id_run_at` ON `job_executions`(`job_id`, `run_at` DESC);
CREATE INDEX `idx_run_at` ON `job_executions`(`run_at` DESC);

-- Scheduled jobs table: tracks jobs scheduled for execution (replaces in-memory map)
CREATE TABLE `scheduled_jobs` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `job_id` BIGINT NOT NULL UNIQUE COMMENT 'Foreign key to jobs table',
  `next_run_time` TIMESTAMP NOT NULL COMMENT 'When this job should be executed next',
  `is_active` BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Whether this schedule is active',
  `schedule_version` BIGINT NULL COMMENT 'Version number to invalidate old schedules',
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (`job_id`) REFERENCES `jobs`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Indexes for faster scheduled job queries
CREATE INDEX `idx_scheduled_jobs_job_id` ON `scheduled_jobs`(`job_id`);
CREATE INDEX `idx_scheduled_jobs_next_run_time` ON `scheduled_jobs`(`next_run_time`);
CREATE INDEX `idx_scheduled_jobs_status` ON `scheduled_jobs`(`job_id`, `is_active`);

-- Optional: Create a view for quick execution statistics
CREATE VIEW `job_execution_stats` AS
SELECT 
  j.id AS job_id,
  j.name AS job_name,
  j.schedule,
  j.api_endpoint,
  j.status,
  j.type,
  COUNT(je.id) AS total_executions,
  COUNT(CASE WHEN je.http_status >= 200 AND je.http_status < 300 THEN 1 END) AS success_count,
  COUNT(CASE WHEN je.http_status >= 400 OR je.error IS NOT NULL THEN 1 END) AS failure_count,
  AVG(je.time_taken_ms) AS avg_time_ms,
  MIN(je.time_taken_ms) AS min_time_ms,
  MAX(je.time_taken_ms) AS max_time_ms,
  MAX(je.run_at) AS last_execution_time,
  MIN(je.run_at) AS first_execution_time
FROM jobs j
LEFT JOIN job_executions je ON j.id = je.job_id
GROUP BY j.id, j.name, j.schedule, j.api_endpoint, j.status, j.type;
