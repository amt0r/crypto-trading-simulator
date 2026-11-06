package com.example.cryptotester.domain.model

data class PendingOrder(
    val id: String = java.util.UUID.randomUUID().toString(),
    val symbol: CryptoSymbol,
    val side: TradeSide,
    val orderType: OrderType,
    val percentage: Double,
    val limitPrice: Double? = null,
    val trailingPercent: Double? = null,
    val activationPrice: Double? = null,
    val displayActivationPrice: Double? = null,
    val isActivated: Boolean = false,
    val peakPrice: Double? = null,
    val initialPrice: Double,
    val displayTargetPrice: Double,
    val createdAt: Long = System.currentTimeMillis()
)
