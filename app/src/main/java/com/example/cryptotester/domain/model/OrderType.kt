package com.example.cryptotester.domain.model

enum class OrderType(val label: String) {
    MARKET("Market"),
    LIMIT("Limit"),
    TRAILING_STOP("Trailing Stop")
}
