package com.example.idempotencygateway.controller;

import com.example.idempotencygateway.model.PaymentRequest;
import com.example.idempotencygateway.service.PaymentService;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> home() {
        return ResponseEntity.ok("Idempotency Gateway is running. Use POST /process-payment to process payments.");
    }

    @PostMapping(value = "/process-payment", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> processPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest paymentRequest) {
        return paymentService.processPayment(idempotencyKey, paymentRequest);
    }
}
