package io.pleo.antaeus.data

import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Customer
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.sql.Connection

class AntaeusDalTest {
    // The tables to create in the database.
    val tables = arrayOf(InvoiceTable, CustomerTable, FailedBillingTable)
    val db = Database
            .connect("jdbc:h2:./data.db:test", "org.h2.Driver")
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


    val dal = AntaeusDal(db = db)

    val pageSize = 2

    @Test
    fun `will fetch 0 Pending Invoices if none available`() {
        val count = dal.fetchPendingInvoices(pageSize).count()
        assertEquals(0, count)
    }


    @Test
    fun `will fetch paged Pending Invoices based on the page Size`() {
        (1..5).forEach { _ ->
            val currency = Currency.DKK
            val money = Money(BigDecimal(1), currency)
            val customerId = dal.createCustomer(currency)?.id
            if( customerId != null) {
                val customer = Customer(customerId, currency)
                dal.createInvoice(money, customer, InvoiceStatus.PENDING)
            }
        }

        assertEquals(5, dal.fetchPendingInvoices(pageSize).count())
    }

    @Test
    fun `will move invoice status to Started on a Pending Invoice`() {
        val currency = Currency.DKK
        val money = Money(BigDecimal(1), currency)
        val customerId = dal.createCustomer(currency)?.id
        val invoice = if( customerId != null) {
            val customer = Customer(customerId, currency)
            dal.createInvoice(money, customer, InvoiceStatus.PENDING)
        } else null

        val updatedInvoice = if(invoice != null) {
            dal.insertStartedPayment(invoice)
            dal.fetchInvoice(invoice.id)
        } else null


        // Assertion
        if(updatedInvoice != null && invoice != null) {
            assertEquals(updatedInvoice.id, invoice.id)
            assertEquals(updatedInvoice.status, InvoiceStatus.STARTED_PAYMENT)
        }

    }

    @Test
    fun `will not move invoice status to Started from a Failed Invoice`() {
        val currency = Currency.DKK
        val money = Money(BigDecimal(1), currency)
        val customerId = dal.createCustomer(currency)?.id
        val invoice = if( customerId != null) {
            val customer = Customer(customerId, currency)
            dal.createInvoice(money, customer, InvoiceStatus.FAILED_PAYMENT)
        } else null

        if(invoice != null) {
            assertThrows<IllegalStateException> {
                dal.insertStartedPayment(invoice)
            }
        }

    }

    @Test
    fun `will move invoice status to Completed on a Started and successful payment`() {
        val currency = Currency.DKK
        val money = Money(BigDecimal(1), currency)
        val customerId = dal.createCustomer(currency)?.id
        val invoice = if( customerId != null) {
            val customer = Customer(customerId, currency)
            dal.createInvoice(money, customer, InvoiceStatus.STARTED_PAYMENT)
        } else null

        val updatedInvoice = if(invoice != null) {
            dal.insertCompletedPayment(invoice)
            dal.fetchInvoice(invoice.id)
        } else null

        // Assertion
        if(updatedInvoice != null && invoice != null) {
            assertEquals(updatedInvoice.id, invoice.id)
            assertEquals(updatedInvoice.status, InvoiceStatus.PAID)
        }

    }

    @Test
    fun `will move invoice status to Failed on a Started and unsuccessful payment`() {
        val currency = Currency.DKK
        val money = Money(BigDecimal(1), currency)
        val customerId = dal.createCustomer(currency)?.id
        val invoice = if( customerId != null) {
            val customer = Customer(customerId, currency)
            dal.createInvoice(money, customer, InvoiceStatus.STARTED_PAYMENT)
        } else null

        val updatedInvoice = if(invoice != null) {
            dal.insertFailedPayment(invoice, "Network error")
            dal.fetchInvoice(invoice.id)
        } else null

        // Assertion
        if(updatedInvoice != null && invoice != null) {
            assertEquals(updatedInvoice.id, invoice.id)
            assertEquals(updatedInvoice.status, InvoiceStatus.FAILED_PAYMENT)
        }

    }
}