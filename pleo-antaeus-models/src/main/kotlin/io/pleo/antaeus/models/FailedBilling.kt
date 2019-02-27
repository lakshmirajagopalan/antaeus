package io.pleo.antaeus.models

data class FailedBilling(
    val id: Int,
    val invoiceId: Int,
    val reason: String
)