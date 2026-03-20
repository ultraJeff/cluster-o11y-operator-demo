package com.example.catalog;

import jakarta.validation.constraints.Min;

public record ReserveRequest(
        @Min(value = 1, message = "quantity must be at least 1") int quantity) {
}
