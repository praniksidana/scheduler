# Job Scheduler Application

A high-performance, production-ready job scheduler that can execute thousands of jobs per second. Each job invokes an HTTP POST API based on a CRON schedule.

## Features

- **High Throughput**: Capable of running 1000s of jobs per second
- **CRON-based Scheduling**: Supports 6-field CRON expressions (including seconds)
- **Execution Types**: Supports both AT_LEAST_ONCE and AT_MOST_ONCE semantics
- **Execution Tracking**: Tracks all job executions with timestamp, HTTP status, and time taken
- **Scalable Architecture**: Uses thread pools and async processing for high concurrency
- **Observability**: Built-in metrics and health endpoints

## Job Spec Format

```json
{
  "schedule": "31 10-15 1 * * MON-FRI",
  "api": "https://localhost:4444/foo",
  "type": "ATLEAST_ONCE"
}
```

- **schedule**: 6-field CRON expression (second minute hour day month day-of-week)
- **api**: HTTP endpoint URL for POST requests
- **type**: Either `ATLEAST_ONCE` or `ATMOST_ONCE`

### Example CRON Expression

The expression `"31 10-15 1 * * MON-FRI"` means:
- Every 31st second
- Of every minute between 10-15 (minutes 10, 11, 12, 13, 14, 15)
- At 1 AM
- Every day of the month
- Every month
- Monday through Friday

## API Endpoints

### 1. Create a Job

**POST** `/v1/schedules/jobs`

**Request Body:**
```json
{
  "schedule": "31 10-15 1 * * MON-FRI",
  "api": "https://localhost:4444/foo",
  "type": "ATLEAST_ONCE"
}
```

**Response:**
```json
{
  "jobId": 1
}
```

### 2. Get Last 10 Executions

**GET** `/v1/schedules/jobs/{jobId}/executions`

**Response:**
```json
[
  {
    "timestamp": "2024-01-15T10:10:31.000",
    "httpStatus": 200,
    "timeTakenMs": 150,
    "error": null
  },
  {
    "timestamp": "2024-01-15T10:10:01.000",
    "httpStatus": 500,
    "timeTakenMs": 5000,
    "error": "Connection timeout"
  }
]
```

## Running the Application

### Prerequisites

- Java 17+
- Maven 3.6+
- MySQL 8.0+ (or use Docker)

### Local Development

1. **Start MySQL** (if not using Docker):
   ```bash
   mysql -u nfcpay -pnfcpay.123 < initScripts/mysql/dump.sql
   ```

2. **Run the application**:
   ```bash
   mvn spring-boot:run
   ```

3. **Run tests**:
   ```bash
   mvn test
   ```

### Using Docker

1. **Build and start all services**:
   ```bash
   docker-compose up --build
   ```

2. **Access the application**:
   - API: http://localhost:8081
   - Metrics: http://localhost:9090/actuator/prometheus
   - Health: http://localhost:8081/actuator/health

### Docker Commands

```bash
# Start services
docker-compose up -d

# View logs
docker-compose logs -f scheduler

# Stop services
docker-compose down

# Rebuild and restart
docker-compose up --build
```

## Configuration

The application uses `application.yaml` for configuration. Key settings:

- **Database**: MySQL connection settings
- **Thread Pools**: Configured for high-throughput execution
- **Timeouts**: 90 seconds maximum for HTTP requests
- **Ports**: 8081 (application), 9090 (metrics)

## Architecture

### Components

1. **JobController**: REST API endpoints
2. **JobService**: Business logic for job management
3. **JobSchedulerService**: CRON-based scheduling engine
4. **JobExecutorService**: HTTP POST execution engine
5. **JobExecutionRepository**: Execution history storage

### Execution Flow

1. Job created via API
2. CRON expression validated and parsed
3. Job scheduled using ScheduledExecutorService
4. At scheduled time, job execution triggered
5. HTTP POST request made to configured API
6. Execution result stored in database

### Scalability

- **Thread Pool**: Up to 1000 concurrent job executions
- **Async Processing**: Non-blocking HTTP client (WebFlux)
- **Database**: Optimized queries with indexes
- **Caching**: In-memory tracking for AT_MOST_ONCE semantics

## Testing

### Unit Tests

- `JobControllerTest`: Tests REST endpoints
- `JobServiceTest`: Tests business logic

### Integration Tests

- `JobSchedulerIntegrationTest`: End-to-end testing

Run all tests:
```bash
mvn test
```

## Production Considerations

### High Availability

For HA deployment:

1. **Multiple Instances**: Run multiple application instances
2. **Database**: Use a replicated MySQL cluster
3. **Load Balancer**: Distribute requests across instances
4. **Job Locking**: Implement distributed locks for critical jobs (can be added)

### Monitoring & Observability

### Metrics Endpoint
- **Prometheus**: `/actuator/prometheus` - All metrics in Prometheus format
- **Health**: `/actuator/health` - Application health status

### Available Metrics

**Job Execution Metrics:**
- `schedulersvc_job_executions_total` - Total job executions (tagged by job_id, job_type)
- `schedulersvc_job_executions_success_total` - Successful executions (tagged by job_id, job_type, http_status)
- `schedulersvc_job_executions_failed_total` - Failed executions (tagged by job_id, job_type, http_status)
- `schedulersvc_job_execution_duration_ms` - Execution duration histogram (tagged by job_id, job_type)

**Scheduler Metrics:**
- `schedulersvc_scheduled_jobs_count` - Gauge: Current number of scheduled jobs
- `schedulersvc_active_jobs_count` - Gauge: Current number of active jobs
- `schedulersvc_scheduler_polls_total` - Number of scheduler poll cycles (tagged by status)

**API Metrics:**
- `schedulersvc_requests_total` - Total API requests (tagged by call_type)
- `schedulersvc_requests_failed_total` - Failed API requests (tagged by call_type, exception_type)

### Example Prometheus Queries

```promql
# Success rate by job type
rate(schedulersvc_job_executions_success_total[5m]) / rate(schedulersvc_job_executions_total[5m])

# Average execution time per job
rate(schedulersvc_job_execution_duration_ms_sum[5m]) / rate(schedulersvc_job_execution_duration_ms_count[5m])

# Failed executions in last hour
sum(increase(schedulersvc_job_executions_failed_total[1h])) by (job_id)

# Current scheduled jobs
schedulersvc_scheduled_jobs_count
```

### Logging
- Structured logging with SLF4J
- Log levels configurable via `application.yaml`

### Performance Tuning

- Adjust thread pool sizes in `SchedulerConfig`
- Tune database connection pool
- Configure JVM heap size based on load

## Example Usage

### Create a Job

```bash
curl -X POST http://localhost:8081/v1/schedules/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "schedule": "*/10 * * * * *",
    "api": "https://httpbin.org/post",
    "type": "ATLEAST_ONCE"
  }'
```

### Get Executions

```bash
curl http://localhost:8081/v1/schedules/jobs/1/executions
```

## License

This is a sample implementation for demonstration purposes.

