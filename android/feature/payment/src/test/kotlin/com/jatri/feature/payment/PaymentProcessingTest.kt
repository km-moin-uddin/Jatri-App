package com.jatri.feature.payment

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jatri.core.data.repository.PaymentRepository
import com.jatri.core.data.repository.PaymentReturnEvents
import com.jatri.core.model.BookingStatus
import com.jatri.core.model.Money
import com.jatri.core.model.PaymentMethod
import com.jatri.core.model.PaymentStart
import com.jatri.core.model.PaymentState
import com.jatri.core.model.PaymentStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PaymentProcessingTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakePayments(var answers: MutableList<PaymentStatus>) : PaymentRepository {
        var calls = 0
        var demoAnswer: Boolean? = null
        override suspend fun start(bookingId: String, method: PaymentMethod, idempotencyKey: String) = PaymentStart("p1", "x", Money.ZERO)
        override suspend fun status(paymentId: String): PaymentState {
            calls++
            val s = if (answers.size > 1) answers.removeAt(0) else answers.first()
            val booking = if (s == PaymentStatus.SUCCESS) BookingStatus.TICKETED else BookingStatus.HELD
            return PaymentState("p1", s, null, "b1", booking, "JTAAAA")
        }
        override suspend fun completeDemo(paymentId: String, success: Boolean) { demoAnswer = success }
        override suspend fun ticketPdf(bookingId: String): File = error("unused")
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(repo: FakePayments, returns: PaymentReturnEvents = PaymentReturnEvents(), demo: Boolean = false) =
        PaymentProcessingViewModel(SavedStateHandle(mapOf("paymentId" to "p1", "demo" to demo, "amountPaisa" to 286000L)), repo, returns)

    @Test
    fun `waits until the server says ticketed`() = runTest(dispatcher) {
        val repo = FakePayments(mutableListOf(PaymentStatus.INITIATED, PaymentStatus.INITIATED, PaymentStatus.SUCCESS))
        var ticketed: String? = null
        vm(repo).bind(onTicketed = { ticketed = it }, onFailed = {})
        runCurrent()
        assertNull(ticketed)
        advanceTimeBy(4_100)
        assertEquals("b1", ticketed)
    }

    @Test
    fun `failed payment goes to the failure screen`() = runTest(dispatcher) {
        var failed = false
        vm(FakePayments(mutableListOf(PaymentStatus.FAILED))).bind(onTicketed = {}, onFailed = { failed = true })
        runCurrent()
        assertTrue(failed)
    }

    @Test
    fun `browser return triggers an immediate check`() = runTest(dispatcher) {
        val repo = FakePayments(mutableListOf(PaymentStatus.INITIATED, PaymentStatus.SUCCESS))
        val returns = PaymentReturnEvents()
        var ticketed: String? = null
        vm(repo, returns).bind(onTicketed = { ticketed = it }, onFailed = {})
        runCurrent()
        returns.onReturn("p1")
        runCurrent()
        assertEquals("b1", ticketed) // before the next 2-second poll
    }

    @Test
    fun `demo gateway waits for the user's answer before polling`() = runTest(dispatcher) {
        val repo = FakePayments(mutableListOf(PaymentStatus.SUCCESS))
        val vm = vm(repo, demo = true)
        vm.bind(onTicketed = {}, onFailed = {})
        runCurrent()
        assertEquals(0, repo.calls)
        assertEquals(ProcessingPhase.DEMO_CHOICE, vm.state.value.phase)
        vm.demoAnswer(true)
        runCurrent()
        assertEquals(true, repo.demoAnswer)
        assertTrue(repo.calls > 0)
    }

    @Test
    fun `refund pending shows the refund message`() = runTest(dispatcher) {
        val vm = vm(FakePayments(mutableListOf(PaymentStatus.REFUND_PENDING)))
        vm.bind(onTicketed = {}, onFailed = {})
        runCurrent()
        assertEquals(ProcessingPhase.REFUND, vm.state.value.phase)
    }

}
