package com.example.idempotencygateway.controller;

import com.example.idempotencygateway.model.PaymentRequest;
import com.example.idempotencygateway.service.PaymentService;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    @PostMapping(value = "/process-payment", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> processPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest paymentRequest) {
        return paymentService.processPayment(idempotencyKey, paymentRequest);
    }
}
