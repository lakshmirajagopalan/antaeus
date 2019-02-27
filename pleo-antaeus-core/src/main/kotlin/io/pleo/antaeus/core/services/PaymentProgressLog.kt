package io.pleo.antaeus.core.services

import io.pleo.antaeus.models.Invoice

/**
 * Contract for an audit log.
 * The provider should also update the invoice table to keep the status up-to-date, with the audit log.
 *
 * In-case we have a transactional queue, we can update the DB and insert to queue in a single transaction.
 */
interface PaymentProgressLog {
    fun startedPayment(invoice: Invoice)
    fun failedPayment(invoice: Invoice, reason: String)
    fun completePayment(invoice: Invoice)
}