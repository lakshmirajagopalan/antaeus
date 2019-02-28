/*
    Implements the data access layer (DAL).
    This file implements the database queries used to fetch and insert rows in our database tables.

    See the `mappings` module for the conversions between database rows and Kotlin objects.
 */

package io.pleo.antaeus.data

import io.pleo.antaeus.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

class AntaeusDal(private val db: Database) {
    fun fetchInvoice(id: Int): Invoice? {
        // transaction(db) runs the internal query as a new database transaction.
        return transaction(db) {
            // Returns the first invoice with matching id.
            InvoiceTable
                .select { InvoiceTable.id.eq(id) }
                .firstOrNull()
                ?.toInvoice()
        }
    }

    fun fetchInvoices(): List<Invoice> {
        return transaction(db) {
            InvoiceTable
                .selectAll()
                .map { it.toInvoice() }
        }
    }

    /**
     * Fetches pending invoices a page at a time to not flood the memory.
     *
     * TODO: Inorder to potentially parallelize/distribute the processing, move the pending to started in the same transaction as fetching.
     */
    fun fetchPendingInvoices(batchSize: Int = 10): Sequence<Invoice> {
        return generateSequence(listOf<Invoice>()){previous  ->
                    transaction(db) {
                        InvoiceTable.select {
                            InvoiceTable.status.eq(InvoiceStatus.PENDING.name) and
                            InvoiceTable.id.greater(if(previous.isEmpty()) 0 else previous.last().id)
                        }.orderBy(InvoiceTable.id)
                         .limit(batchSize)
                         .map { it.toInvoice() }
                    }
                }
                .drop(1)
                .takeWhile { it.isNotEmpty() }
                .flatMap { it.asSequence() }
    }

    /**
     * Fetches invoices by status a page at a time to not flood the memory.
     */
    fun fetchInvoicesByStatus(invoiceStatus: InvoiceStatus, pageNumber: Int, pageSize: Int = 10): List<Invoice> {
        return transaction(db) {
            InvoiceTable.select {
                InvoiceTable.status.eq(invoiceStatus.name)
            }
            .orderBy(InvoiceTable.id)
            .limit(pageSize,pageSize * (pageNumber - 1))
            .map { it.toInvoice() }
        }
    }

    fun createInvoice(amount: Money, customer: Customer, status: InvoiceStatus = InvoiceStatus.PENDING): Invoice? {
        val id = transaction(db) {
            // Insert the invoice and returns its new id.
            InvoiceTable
                .insert {
                    it[this.value] = amount.value
                    it[this.currency] = amount.currency.toString()
                    it[this.status] = status.toString()
                    it[this.customerId] = customer.id
                } get InvoiceTable.id
        }

        return fetchInvoice(id!!)
    }

    fun fetchCustomer(id: Int): Customer? {
        return transaction(db) {
            CustomerTable
                .select { CustomerTable.id.eq(id) }
                .firstOrNull()
                ?.toCustomer()
        }
    }

    fun fetchCustomers(): List<Customer> {
        return transaction(db) {
            CustomerTable
                .selectAll()
                .map { it.toCustomer() }
        }
    }

    fun createCustomer(currency: Currency): Customer? {
        val id = transaction(db) {
            // Insert the customer and return its new id.
            CustomerTable.insert {
                it[this.currency] = currency.toString()
            } get CustomerTable.id
        }

        return fetchCustomer(id!!)
    }

    /**
     * Move the Invoice from Pending -> Started Payment.
     */
    fun insertStartedPayment(invoice: Invoice): Invoice? {
        return transaction(db) {
            moveStatus(invoice, InvoiceStatus.PENDING, InvoiceStatus.STARTED_PAYMENT)
        }
    }

    /**
     * Move the Invoice from Started Payment -> Failed Payment, also add the failure to the failure table.
     */
    fun insertFailedPayment(invoice: Invoice, reason: String, message: String, timestamp: DateTime): Invoice? {
        return transaction (db) {
            FailedBillingTable.insert {
                it[this.invoiceId] = invoice.id
                it[this.reason] = reason
                it[this.message] = message
                it[this.timestamp] = timestamp
            }
            moveStatus(invoice, InvoiceStatus.STARTED_PAYMENT, InvoiceStatus.FAILED_PAYMENT)
        }
    }

    /**
     * Move the invoice from Started Payment -> Paid.
     */
    fun insertCompletedPayment(invoice: Invoice): Invoice? {
        return transaction (db) {
            moveStatus(invoice, InvoiceStatus.STARTED_PAYMENT, InvoiceStatus.PAID)
        }
    }

    /**
     * Move the invoice to Pending
     */
    fun forceRequeueForBilling(invoiceId: Int): Invoice? {
        transaction(db) {
            InvoiceTable.update({ InvoiceTable.id eq invoiceId }) {
                it[status] = InvoiceStatus.PENDING.toString()
            }
        }
        return fetchInvoice(invoiceId)
    }


    private fun moveStatus(invoice: Invoice, from: InvoiceStatus, to: InvoiceStatus): Invoice? {
        val updated = InvoiceTable.update({
            (InvoiceTable.id eq invoice.id) and (InvoiceTable.status eq from.toString())
        }) { it[status] = to.toString() }

        if(updated != 1)
            throw IllegalStateException("No invoice id = ${invoice.id} and state = ${from} updated to ${to}")

        return fetchInvoice(invoice.id)
    }

    fun getFailedBilling(invoiceId: Int): List<FailedBilling> {
        return transaction(db) {
            FailedBillingTable
                .select { FailedBillingTable.invoiceId.eq(invoiceId) }
                    .orderBy(FailedBillingTable.timestamp)
                    .map { it.toFailedBilling() }

        }
    }

}
