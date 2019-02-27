package io.pleo.antaeus.data

import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Customer
import io.pleo.antaeus.models.InvoiceStatus.*
import io.pleo.antaeus.models.Money
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.sql.Connection

class AntaeusDalTest {
    // The tables to create in the database.
    private val tables = arrayOf(InvoiceTable, CustomerTable, FailedBillingTable)
    private val db = Database
            .connect("jdbc:sqlite:./data.db:test", "org.sqlite.JDBC")
            .also {
                TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
                transaction(it) {
                    addLogger(StdOutSqlLogger)
                    // Drop all existing tables to ensure a clean slate on each run
                    SchemaUtils.drop(*tables)
                    // Create all tables
                    SchemaUtils.create(*tables)
                }
            }


    private val dal = AntaeusDal(db = db)

    private val batchSize = 2
    private val currency = Currency.DKK
    private val money = Money(BigDecimal(1), currency)
    private val customerId = dal.createCustomer(currency)?.id!!
    private val customer = Customer(customerId, currency)

    @Test
    fun `will fetch 0 Pending Invoices if none available`() {
        val count = dal.fetchPendingInvoices(batchSize).count()

        assertEquals(0, count)
    }


    @Test
    fun `will fetch Pending Invoices by batchSize`() {
        (1..5).forEach { _ ->
            val customer = Customer(customerId, currency)
            dal.createInvoice(money, customer, PENDING)
        }

        assertEquals(5, dal.fetchPendingInvoices(batchSize).count())
    }

    @Test
    fun `will move invoice status to Started on a Pending Invoice`() {
        val invoice = dal.createInvoice(money, customer, PENDING)!!

        val updatedInvoice = dal.insertStartedPayment(invoice)!!

        assertEquals(updatedInvoice, invoice.copy(status = STARTED_PAYMENT))
    }

    @Test
    fun `will not move invoice status to Started from a Failed Invoice`() {
        val invoice = dal.createInvoice(money, customer, FAILED_PAYMENT)!!

        assertThrows<IllegalStateException> {
            dal.insertStartedPayment(invoice)
        }
    }

    @Test
    fun `will move invoice status to Completed on a Started and successful payment`() {
        val invoice = dal.createInvoice(money, customer, STARTED_PAYMENT)!!

        val updatedInvoice = dal.insertCompletedPayment(invoice)!!

        assertEquals(updatedInvoice, invoice.copy(status = PAID))
    }

    @Test
    fun `will move invoice status to Failed on a Started and unsuccessful payment`() {
        val invoice = dal.createInvoice(money, customer, STARTED_PAYMENT)!!

        val updatedInvoice = dal.insertFailedPayment(invoice, "Network error")!!

        assertEquals(updatedInvoice, invoice.copy(status = FAILED_PAYMENT))
    }

    @Test
    fun `will add a Failed Billing record to the FailedBillingTable for later reconciliation on unsuccessful payment`() {
        val invoice = dal.createInvoice(money, customer, STARTED_PAYMENT)!!

        val failureReason = "Network error"
        dal.insertFailedPayment(invoice, failureReason)!!
        val mayBeFailedBillingRecord = dal.getFailedBilling(invoice.id)

        assertNotNull(mayBeFailedBillingRecord)
        assertEquals(mayBeFailedBillingRecord?.invoiceId, invoice.id)
        assertEquals(mayBeFailedBillingRecord?.reason, failureReason)
    }

    @Test
    fun `will fetch invoice by status`() {
        val failedInvoice = dal.createInvoice(money, customer, FAILED_PAYMENT)!!

        dal.createInvoice(money, customer, PAID)!!

        val invoiceResponses = dal.fetchInvoicesByStatus(FAILED_PAYMENT, 1, batchSize)

        assertEquals(invoiceResponses.count(), 1)
        val invoiceResponse = invoiceResponses[0]
        assertEquals(invoiceResponse, failedInvoice)
    }

    @Test
    fun `will fetch invoice by status and pageNumber`() {
        val invoices = (0..4).map { dal.createInvoice(money, customer, PAID) }.filterNotNull()

        val failedInvoices = dal.fetchInvoicesByStatus(PAID, 2, batchSize)

        assertEquals(failedInvoices.count(), 2)
        assertEquals(failedInvoices[0].id, invoices[2].id)
        assertEquals(failedInvoices[1].id, invoices[3].id)
    }

    @Test
    fun `will move invoice status to Pending to retry billing`() {
        val failedInvoice = dal.createInvoice(money, customer, FAILED_PAYMENT)!!

        val updatedInvoice = dal.forceRequeueForBilling(failedInvoice.id)!!

        assertEquals(updatedInvoice, failedInvoice.copy(status = PENDING))
    }
}