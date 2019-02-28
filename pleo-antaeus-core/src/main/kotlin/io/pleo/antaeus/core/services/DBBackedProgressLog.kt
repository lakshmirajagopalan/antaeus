package io.pleo.antaeus.core.services

import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import org.joda.time.DateTime

/**
 * Transitions the status in DB and also inserts into failure in-case of failures.
 * Ideally this should just be leaving a log for audit trail and have only inserts.
 * We are kind of using DB as a State machine, for the sake of simplicity.
 */
class DBBackedProgressLog(private val dal: AntaeusDal) : PaymentProgressLog {
    override fun startedPayment(invoice: Invoice) {
        dal.insertStartedPayment(invoice)
    }

    override fun failedPayment(invoice: Invoice, reason: String, message: String, timestamp: DateTime) {
        dal.insertFailedPayment(invoice, reason, message, timestamp)
    }

    override fun completePayment(invoice: Invoice) {
        dal.insertCompletedPayment(invoice)
    }
}