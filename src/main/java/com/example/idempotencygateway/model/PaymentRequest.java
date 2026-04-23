package com.example.idempotencygateway.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public class PaymentRequest {

    @Positive(message = "amount must be greater than zero")
    private int amount;

    @NotBlank(message = "currency is required")
    private String currency;

    public PaymentRequest() {
    }

    public PaymentRequest(int amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
