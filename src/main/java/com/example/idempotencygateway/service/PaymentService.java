package com.example.idempotencygateway.service;

import com.example.idempotencygateway.model.PaymentRequest;
import com.example.idempotencygateway.model.RequestStatus;
import com.example.idempotencygateway.model.StoredRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    private final ConcurrentHashMap<String, StoredRequest> requestStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final long processingDelayMs;
    private final long entryTtlMs;

    public PaymentService(
            ObjectMapper objectMapper,
            @Value("${payment.processing.delay.ms:2000}") long processingDelayMs,
            @Value("${idempotency.entry.ttl.ms:600000}") long entryTtlMs) {
        this.objectMapper = objectMapper;
        this.processingDelayMs = processingDelayMs;
        this.entryTtlMs = entryTtlMs;
    }

    public ResponseEntity<String> processPayment(String idempotencyKey, PaymentRequest paymentRequest) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required");
        }

        evictExpiredEntries();
        String serializedRequest = serializeRequest(paymentRequest);

        while (true) {
            StoredRequest newEntry = new StoredRequest(serializedRequest, RequestStatus.PROCESSING);
            StoredRequest existingEntry = requestStore.putIfAbsent(idempotencyKey, newEntry);

            if (existingEntry == null) {
                return handleFreshRequest(idempotencyKey, paymentRequest, newEntry);
            }

            if (!existingEntry.getOriginalRequestBody().equals(serializedRequest)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Idempotency key already used for a different request body");
            }

            if (existingEntry.getStatus() == RequestStatus.PROCESSING) {
                waitForCompletion(idempotencyKey, existingEntry);
            }

            if (existingEntry.getStatus() == RequestStatus.COMPLETED) {
                logger.info("Returning cached payment response for key {}", idempotencyKey);
                return ResponseEntity.status(existingEntry.getResponseStatus())
                        .header("X-Cache-Hit", "true")
                        .body(existingEntry.getResponse());
            }

            requestStore.remove(idempotencyKey, existingEntry);
        }
    }

    private ResponseEntity<String> handleFreshRequest(
            String idempotencyKey,
            PaymentRequest paymentRequest,
            StoredRequest storedRequest) {
        try {
            logger.info("Processing payment for key {}", idempotencyKey);
            TimeUnit.MILLISECONDS.sleep(processingDelayMs);
            String response = "Charged %d %s".formatted(paymentRequest.getAmount(), paymentRequest.getCurrency());
            storedRequest.markCompleted(response, HttpStatus.CREATED);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.LOCATION, "/process-payment/" + idempotencyKey);
            return new ResponseEntity<>(response, headers, HttpStatus.CREATED);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            storedRequest.markFailed();
            requestStore.remove(idempotencyKey, storedRequest);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Payment processing interrupted");
        } catch (RuntimeException ex) {
            storedRequest.markFailed();
            requestStore.remove(idempotencyKey, storedRequest);
            throw ex;
        }
    }

    private void waitForCompletion(String idempotencyKey, StoredRequest storedRequest) {
        try {
            logger.info("Waiting for in-flight request to finish for key {}", idempotencyKey);
            storedRequest.getCompletionLatch().await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Interrupted while waiting for payment result");
        }
    }

    private String serializeRequest(PaymentRequest paymentRequest) {
        try {
            return objectMapper.writeValueAsString(paymentRequest);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read payment request");
        }
    }

    private void evictExpiredEntries() {
        requestStore.entrySet().removeIf(entry -> entry.getValue().isExpired(entryTtlMs));
    }
}
