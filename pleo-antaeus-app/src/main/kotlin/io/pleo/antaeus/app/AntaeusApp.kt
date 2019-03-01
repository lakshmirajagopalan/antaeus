/*
    Defines the main() entry point of the app.
    Configures the database and sets up the REST web service.
 */

@file:JvmName("AntaeusApp")

package io.pleo.antaeus.app

import getPaymentProvider
import io.pleo.antaeus.app.TimedScheduler.Companion.startOfEveryMonth
import io.pleo.antaeus.core.services.*
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.data.CustomerTable
import io.pleo.antaeus.data.FailedBillingTable
import io.pleo.antaeus.data.InvoiceTable
import io.pleo.antaeus.rest.AntaeusRest
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import setupInitialData
import java.sql.Connection

fun main() {
    // The tables to create in the database.
    val tables = arrayOf(InvoiceTable, CustomerTable, FailedBillingTable)

    // Connect to the database and create the needed tables. Drop any existing data.
    val db = Database
        .connect("jdbc:sqlite:/tmp/data.db", "org.sqlite.JDBC")
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

    // Set up data access layer.
    val dal = AntaeusDal(db = db)

    // Insert example data in the database.
    setupInitialData(dal = dal)

    // Get third parties
    val paymentProvider = getPaymentProvider()

    // Create core services
    val invoiceService = InvoiceService(dal = dal)
    val customerService = CustomerService(dal = dal)

    // Decorated Payment Provider with Log

    // Using db to track payment progress for simplicity here
    // The better option would be to use transactional queues for audit trails
    val dbBackedProgressLog = DBBackedProgressLog(dal = dal)
    val progressTrackingPaymentProvider = ProgressTrackingPaymentProvider(paymentProvider = paymentProvider, paymentProgressLog = dbBackedProgressLog)

    // This is _your_ billing service to be included where you see fit
    val billingService = BillingService(paymentProvider = progressTrackingPaymentProvider, invoiceService = invoiceService)

    val scheduler = TimedScheduler()

    scheduler.schedule(
            startOfEveryMonth(),
            billingService::chargePendingInvoices
    )

    // Create REST web service
    AntaeusRest(
        invoiceService = invoiceService,
        customerService = customerService,
        billingService = billingService
    ).run()
}

