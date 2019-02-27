package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class BillingServiceTest {

    private val customerId = 101
    private val amount = Money(BigDecimal(100), Currency.DKK)

    private val pendingInvoice = Invoice(0,
            customerId = customerId,
            amount = amount,
            status = InvoiceStatus.PENDING)

    private val mockedInvoiceService = mockk<InvoiceService> {
        every { fetchPendingInvoices() } returns listOf(pendingInvoice).asSequence()
    }

    @Test
    fun `will fetch pending invoices from InvoiceService`() {

        val paymentProvider = mockk<PaymentProvider> {
            every { charge(pendingInvoice) } returns true
        }
        val billingService = BillingService(
                paymentProvider = paymentProvider,
                invoiceService = mockedInvoiceService)

        billingService.chargePendingInvoices()
        verify { mockedInvoiceService.fetchPendingInvoices() }
        verify { paymentProvider.charge(pendingInvoice) }
    }

}