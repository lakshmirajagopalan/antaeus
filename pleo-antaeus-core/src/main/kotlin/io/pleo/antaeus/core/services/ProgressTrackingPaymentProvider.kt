package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Invoice
import mu.KotlinLogging
import org.joda.time.DateTime
import java.sql.SQLException

/**
 * A payment provider, which tracks the status of the invoice in the DB.
 */
class ProgressTrackingPaymentProvider(private val paymentProvider: PaymentProvider,
                                      private val paymentProgressLog: PaymentProgressLog) : PaymentProvider {

    private val logger = KotlinLogging.logger {}

    override fun charge(invoice: Invoice) : Boolean {
        try {
            paymentProgressLog.startedPayment(invoice)
            val chargedStatus = paymentProvider.charge(invoice)
            if(chargedStatus)
                paymentProgressLog.completePayment(invoice)
            else
                paymentProgressLog.failedPayment(invoice, "Insufficient Balance", "Insufficient Balance", DateTime.now())
            return chargedStatus
        } catch (e: Exception) {
            val failureReason = failPaymentReason(e)
            paymentProgressLog.failedPayment(invoice, failureReason, e.message ?: "", DateTime.now())
            logger.error(e) { "Failed transaction while paying invoice id = ${invoice.id} reason = ${failureReason}"}
            return false
        }

    }

    // Could be a polymorphic function inside PleoException
    // Adding it as an internal function in order to not change existing code
    private fun failPaymentReason(e: Exception): String {
        return when (e) {
            is NetworkException -> "Network Error"
            is CustomerNotFoundException -> "Unknown Customer"
            is CurrencyMismatchException -> "Currency Mismatch Error"
            else -> "Unknown error ${e.message}"
        }
    }
}