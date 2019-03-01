package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import mu.KotlinLogging


class BillingService(
    private val paymentProvider: PaymentProvider,
    private val invoiceService: InvoiceService
) {

    private val logger = KotlinLogging.logger {}

    fun chargePendingInvoices() {

        logger.info { "Starting billing of pending invoices"}

        invoiceService.fetchPendingInvoices().forEach { invoice ->
            paymentProvider.charge(invoice)
        }
    }
}