package io.pleo.antaeus.models

import org.joda.time.DateTime

data class FailedBilling(
    val id: Int,
    val invoiceId: Int,
    val reason: String,
    val message: String,
    val timestamp: DateTime
)