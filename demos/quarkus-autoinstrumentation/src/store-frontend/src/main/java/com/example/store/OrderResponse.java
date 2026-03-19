package com.example.store;

public record OrderResponse(String orderId, String productId, String productName,
                             int quantity, double total, String status) {
}
