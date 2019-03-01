/*
    Configures the rest app along with basic exception handling and URL endpoints.
 */

package io.pleo.antaeus.rest

import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.joda.JodaModule
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.json.JavalinJackson
import io.pleo.antaeus.core.exceptions.EntityNotFoundException
import io.pleo.antaeus.core.services.BillingService
import io.pleo.antaeus.core.services.CustomerService
import io.pleo.antaeus.core.services.InvoiceService
import io.pleo.antaeus.models.InvoiceStatus
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class AntaeusRest (
    private val invoiceService: InvoiceService,
    private val customerService: CustomerService,
    private val billingService: BillingService
) : Runnable {

    override fun run() {
        app.start(7000)
    }

    // Set up Javalin rest app
    private val app = Javalin
        .create()
        .apply {
            JavalinJackson.getObjectMapper().registerModule(JodaModule())

            // InvoiceNotFoundException: return 404 HTTP status code
            exception(EntityNotFoundException::class.java) { _, ctx ->
                ctx.status(404)
            }
            // Unexpected exception: return HTTP 500
            exception(Exception::class.java) { e, _ ->
                logger.error(e) { "Internal server error" }
            }
            // On 404: return message
            error(404) { ctx -> ctx.json("not found") }
        }

    init {
        // Set up URL endpoints for the rest app
        app.routes {
           path("rest") {
               // Route to check whether the app is running
               // URL: /rest/health
               get("health") {
                   it.json("ok")
               }

               // V1
               path("v1") {
                   path("invoices") {
                       // URL: /rest/v1/invoices
                       get {
                           it.json(invoiceService.fetchAll())
                       }

                       // URL: /rest/v1/invoices/{:id}
                       get(":id") {
                          it.json(invoiceService.fetch(it.pathParam("id").toInt()))
                       }

                       // URL: /rest/v1/invoices/{:id}/requeue
                       post(":id/requeue") {
                           it.json(invoiceService.requeueForBilling(it.pathParam("id").toInt()))
                       }

                       path("status") {
                           // URL: /rest/v1/invoices/status/{:status}
                           get(":status") {
                               val invoiceStatuses = InvoiceStatus.values().map{ it.name }
                               val statusStr = it.validatedPathParam("status")
                                       .check({status -> invoiceStatuses.contains(status.toUpperCase())}, "status should be one of the InvoiceStatuses: ${invoiceStatuses.joinToString()}")
                                       .getOrThrow().toUpperCase()
                               val status = InvoiceStatus.valueOf(statusStr)
                               val pageNumber = it.validatedQueryParam("page", "1")
                                       .asInt()
                                       .check({ page -> page > 0 }, "Page has to be a positive Int")
                                       .getOrThrow()

                               it.json(invoiceService.fetchByStatus(status, pageNumber))
                           }
                       }

                       path("failed") {
                           // URL: /rest/v1/invoices/failed/{:invoiceid}
                           get(":invoiceid") {
                               it.json(invoiceService.fetchFailedBilling(it.pathParam("invoiceid").toInt()))
                           }
                       }
                   }

                   path("billing/force") {
                       post {
                           billingService.chargePendingInvoices()
                       }
                   }

                   path("customers") {
                       // URL: /rest/v1/customers
                       get {
                           it.json(customerService.fetchAll())
                       }

                       // URL: /rest/v1/customers/{:id}
                       get(":id") {
                           it.json(customerService.fetch(it.pathParam("id").toInt()))
                       }
                   }
               }
           }
        }
    }
}
