package com.fampay.scheduler.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fampay.scheduler.aspects.ControllerAdvice;
import com.fampay.scheduler.clients.MetricsClient;
import com.fampay.scheduler.enums.JobType;
import com.fampay.scheduler.models.JobExecution;
import com.fampay.scheduler.models.JobSpec;
import com.fampay.scheduler.service.JobService;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@RunWith(MockitoJUnitRunner.class)
public class JobControllerTest {

  private MockMvc mockMvc;

  @Mock
  private JobService jobService;

  @InjectMocks
  private JobController jobController;
  @Mock
  private MetricsClient metricsClient;

  private ObjectMapper objectMapper = new ObjectMapper();

  private JobSpec jobSpec;
  private Long jobId;

  @Before
  public void setUp() {
    PageableHandlerMethodArgumentResolver pageableResolver = new PageableHandlerMethodArgumentResolver();
    this.mockMvc = MockMvcBuilders.standaloneSetup(jobController)
        .setCustomArgumentResolvers(pageableResolver)
        .setControllerAdvice(new ControllerAdvice())
        .build();
    jobSpec = new JobSpec();
    jobSpec.setSchedule("31 10-15 1 * * MON-FRI");
    jobSpec.setApi("https://localhost:4444/foo");
    jobSpec.setType(JobType.ATLEAST_ONCE);
    jobId = 1L;
  }

  @Test
  public void testCreateJob_Success() throws Exception {
    when(jobService.createJobFromSpec(any(JobSpec.class))).thenReturn(jobId);

    mockMvc
        .perform(
            post("/v1/schedules/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(jobSpec)))
        .andExpect(status().isCreated());
  }

  @Test
  public void testCreateJob_InvalidSchedule() throws Exception {
    jobSpec.setSchedule("");

    mockMvc
        .perform(
            post("/v1/schedules/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(jobSpec)))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testCreateJob_InvalidApi() throws Exception {
    jobSpec.setApi("");

    mockMvc
        .perform(
            post("/v1/schedules/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(jobSpec)))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testGetExecutions_Success_WithDefaultLimit() throws Exception {
    LocalDateTime now = LocalDateTime.now();
    JobExecution exec1 =
        JobExecution.builder()
            .timestamp(now)
            .httpStatus(200)
            .timeTakenMs(100L)
            .error(null)
            .build();
    JobExecution exec2 =
        JobExecution.builder()
            .timestamp(now.minusMinutes(1))
            .httpStatus(500)
            .timeTakenMs(5000L)
            .error("Timeout")
            .build();

    List<JobExecution> executions = Arrays.asList(exec1, exec2);
    Pageable pageable = PageRequest.of(0, 10);

    when(jobService.getExecutions(any(), any())).thenReturn(executions);

    mockMvc
        .perform(get("/v1/schedules/jobs/1/executions"))
        .andExpect(status().isOk());
  }

  @Test
  public void testGetExecutions_Success_WithCustomLimit() throws Exception {
    LocalDateTime now = LocalDateTime.now();
    JobExecution exec1 =
        JobExecution.builder()
            .timestamp(now)
            .httpStatus(200)
            .timeTakenMs(100L)
            .error(null)
            .build();

    List<JobExecution> executions = Arrays.asList(exec1);
    Pageable pageable = PageRequest.of(0, 10);


    mockMvc
        .perform(get("/v1/schedules/jobs/1/executions"))
        .andExpect(status().isOk());
  }

  @Test
  public void testGetExecutions_InvalidJobId() throws Exception {
    Pageable pageable = PageRequest.of(0, 10);

    when(jobService.getExecutions(any(), any()))
        .thenThrow(new com.fampay.scheduler.exception.InvalidReqParameters("Invalid job ID"));

    mockMvc
        .perform(get("/v1/schedules/jobs/invalid/executions"))
        .andExpect(status().isBadRequest());
  }
}

