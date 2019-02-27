package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ProgressTrackingPaymentProviderTest {

    private val customerId = 101
    private val amount = Money(BigDecimal(100), Currency.DKK)

    private val pendingInvoice = Invoice(0, customerId = customerId, amount = amount, status = InvoiceStatus.PENDING)


    @Test
    fun `will charge a Pending invoice and update status to PAID on success`() {

        val mockedPaymentProvider = mockk<PaymentProvider> {
            every { charge(invoice = pendingInvoice) } returns true
        }

        val mockedPaymentProgressLog = mockk<PaymentProgressLog> {
            every { completePayment(pendingInvoice) } returns Unit
            every { startedPayment(pendingInvoice) } returns Unit
        }

        val progressTrackingPaymentProvider = ProgressTrackingPaymentProvider(mockedPaymentProvider, mockedPaymentProgressLog)

        assertTrue(progressTrackingPaymentProvider.charge(pendingInvoice))
        verify { mockedPaymentProgressLog.startedPayment(pendingInvoice) }
        verify { mockedPaymentProgressLog.completePayment(pendingInvoice) }
    }

    @Test
    fun `will fail a Pending invoice on NetworkError`() {

        val mockedPaymentProvider = mockk<PaymentProvider> {
            every { charge(invoice = pendingInvoice) } throws NetworkException()
        }

        val mockedPaymentProgressLog = mockk<PaymentProgressLog> {
            every { startedPayment(pendingInvoice) } returns Unit
            every { failedPayment(pendingInvoice, any()) } returns Unit
        }

        val progressTrackingPaymentProvider = ProgressTrackingPaymentProvider(mockedPaymentProvider, mockedPaymentProgressLog)

        assertFalse(progressTrackingPaymentProvider.charge(pendingInvoice))
        verify { mockedPaymentProgressLog.startedPayment(pendingInvoice) }
        verify { mockedPaymentProgressLog.failedPayment(pendingInvoice, any()) }

    }

    @Test
    fun `will fail a Pending invoice on CustomerNotFound`() {

        val mockedPaymentProvider = mockk<PaymentProvider> {
            every { charge(invoice = pendingInvoice) } throws CustomerNotFoundException(customerId)
        }

        val mockedPaymentProgressLog = mockk<PaymentProgressLog> {
            every { startedPayment(pendingInvoice) } returns Unit
            every { failedPayment(pendingInvoice, any()) } returns Unit
        }

        val progressTrackingPaymentProvider = ProgressTrackingPaymentProvider(mockedPaymentProvider, mockedPaymentProgressLog)

        assertFalse(progressTrackingPaymentProvider.charge(pendingInvoice))
        verify { mockedPaymentProgressLog.startedPayment(pendingInvoice) }
        verify { mockedPaymentProgressLog.failedPayment(pendingInvoice, any()) }
    }

    @Test
    fun `will fail a Pending invoice on CurrencyMismatch`() {

        val mockedPaymentProvider = mockk<PaymentProvider> {
            every { charge(invoice = pendingInvoice) } throws CurrencyMismatchException(pendingInvoice.id, customerId)
        }

        val mockedPaymentProgressLog = mockk<PaymentProgressLog> {
            every { startedPayment(pendingInvoice) } returns Unit
            every { failedPayment(pendingInvoice, any()) } returns Unit
        }

        val progressTrackingPaymentProvider = ProgressTrackingPaymentProvider(mockedPaymentProvider, mockedPaymentProgressLog)

        assertFalse(progressTrackingPaymentProvider.charge(pendingInvoice))
        verify { mockedPaymentProgressLog.startedPayment(pendingInvoice) }
        verify { mockedPaymentProgressLog.failedPayment(pendingInvoice, any()) }
    }

    @Test
    fun `will fail a Pending invoice on Insufficient Balance on the customer side`() {

        val mockedPaymentProvider = mockk<PaymentProvider> {
            every { charge(invoice = pendingInvoice) } returns false
        }

        val mockedPaymentProgressLog = mockk<PaymentProgressLog> {
            every { startedPayment(pendingInvoice) } returns Unit
            every { failedPayment(pendingInvoice, any()) } returns Unit
        }

        val progressTrackingPaymentProvider = ProgressTrackingPaymentProvider(mockedPaymentProvider, mockedPaymentProgressLog)

        assertFalse(progressTrackingPaymentProvider.charge(pendingInvoice))
        verify { mockedPaymentProgressLog.startedPayment(pendingInvoice) }
        verify { mockedPaymentProgressLog.failedPayment(pendingInvoice, any()) }
    }

}