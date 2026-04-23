package com.example.idempotencygateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.idempotencygateway.model.PaymentRequest;
import com.example.idempotencygateway.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "payment.processing.delay.ms=200",
        "idempotency.entry.ttl.ms=600000"
})
class PaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentService paymentService;

    @Test
    void firstRequestProcessesAndSecondRequestReturnsCachedResponse() throws Exception {
        String body = objectMapper.writeValueAsString(new PaymentRequest(100, "USD"));

        long firstStart = System.currentTimeMillis();
        MvcResult firstResult = mockMvc.perform(post("/process-payment")
                        .header("Idempotency-Key", "payment-123")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        long firstDuration = System.currentTimeMillis() - firstStart;

        long secondStart = System.currentTimeMillis();
        MvcResult secondResult = mockMvc.perform(post("/process-payment")
                        .header("Idempotency-Key", "payment-123")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Cache-Hit", "true"))
                .andReturn();
        long secondDuration = System.currentTimeMillis() - secondStart;

        assertThat(firstResult.getResponse().getContentAsString()).isEqualTo("Charged 100 USD");
        assertThat(secondResult.getResponse().getContentAsString()).isEqualTo("Charged 100 USD");
        assertThat(firstResult.getResponse().getHeader("Location")).isEqualTo("/process-payment/payment-123");
        assertThat(secondResult.getResponse().getHeader("Location")).isEqualTo("/process-payment/payment-123");
        assertThat(firstDuration).isGreaterThanOrEqualTo(150L);
        assertThat(secondDuration).isLessThan(firstDuration);
    }

    @Test
    void sameKeyWithDifferentBodyReturnsConflict() throws Exception {
        String firstBody = objectMapper.writeValueAsString(new PaymentRequest(100, "USD"));
        String secondBody = objectMapper.writeValueAsString(new PaymentRequest(250, "EUR"));

        mockMvc.perform(post("/process-payment")
                        .header("Idempotency-Key", "payment-456")
                        .contentType("application/json")
                        .content(firstBody))
                .andExpect(status().isCreated());

        MvcResult conflictResult = mockMvc.perform(post("/process-payment")
                        .header("Idempotency-Key", "payment-456")
                        .contentType("application/json")
                        .content(secondBody))
                .andExpect(status().isConflict())
                .andReturn();

        assertThat(conflictResult.getResponse().getContentAsString())
                .isEqualTo("Idempotency key already used for a different request body");
    }

    @Test
    void missingIdempotencyKeyReturnsBadRequest() throws Exception {
        String body = objectMapper.writeValueAsString(new PaymentRequest(5000, "RWF"));

        mockMvc.perform(post("/process-payment")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentRequestsWithSameKeyShareOneProcessingResult() throws ExecutionException, InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        PaymentRequest paymentRequest = new PaymentRequest(300, "ZAR");

        Callable<ResponseEntity<String>> task = () -> paymentService.processPayment("payment-789", paymentRequest);
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
        futures.add(executorService.submit(task));
        futures.add(executorService.submit(task));

        ResponseEntity<String> first = futures.get(0).get();
        ResponseEntity<String> second = futures.get(1).get();

        executorService.shutdown();

        assertThat(first.getBody()).isEqualTo("Charged 300 ZAR");
        assertThat(second.getBody()).isEqualTo("Charged 300 ZAR");
        assertThat(first.getStatusCode().value()).isEqualTo(201);
        assertThat(second.getStatusCode().value()).isEqualTo(201);
        assertThat(first.getHeaders().getFirst("X-Cache-Hit")).isIn((String) null, "true");
        assertThat(second.getHeaders().getFirst("X-Cache-Hit")).isIn((String) null, "true");
        assertThat(first.getHeaders().getFirst("X-Cache-Hit") == null
                || second.getHeaders().getFirst("X-Cache-Hit") == null).isTrue();
    }
}
