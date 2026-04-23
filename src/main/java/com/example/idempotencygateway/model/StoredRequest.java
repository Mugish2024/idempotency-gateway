package com.example.idempotencygateway.model;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;

import org.springframework.http.HttpStatus;

public class StoredRequest {

    private final String originalRequestBody;
    private final CountDownLatch completionLatch = new CountDownLatch(1);
    private final Instant createdAt = Instant.now();
    private volatile RequestStatus status;
    private volatile String response;
    private volatile HttpStatus responseStatus;
    private volatile String location;
    private volatile Instant completedAt;

    public StoredRequest(String originalRequestBody, RequestStatus status) {
        this.originalRequestBody = originalRequestBody;
        this.status = status;
    }

    public String getOriginalRequestBody() {
        return originalRequestBody;
    }

    public CountDownLatch getCompletionLatch() {
        return completionLatch;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public String getResponse() {
        return response;
    }

    public HttpStatus getResponseStatus() {
        return responseStatus;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getLocation() {
        return location;
    }

    public void markCompleted(String response, HttpStatus responseStatus, String location) {
        this.response = response;
        this.responseStatus = responseStatus;
        this.location = location;
        this.status = RequestStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.completionLatch.countDown();
    }

    public void markFailed() {
        this.status = RequestStatus.FAILED;
        this.completedAt = Instant.now();
        this.completionLatch.countDown();
    }

    public boolean isExpired(long ttlMillis) {
        if (completedAt == null) {
            return false;
        }
        return completedAt.plusMillis(ttlMillis).isBefore(Instant.now());
    }
}
