package org.lightningdevkit.ldkserver.remote.ui.send

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.lightningdevkit.ldkserver.client.UnifiedSendResult
import org.lightningdevkit.ldkserver.remote.service.FakeLdkService

@OptIn(ExperimentalCoroutinesApi::class)
class SendViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun starts_in_input_step_with_empty_state() =
        runTest(dispatcher) {
            val vm = SendViewModel(FakeLdkService())
            val s = vm.state.value
            assertEquals(SendStep.Input, s.step)
            assertTrue(s.input.isEmpty())
            assertNull(s.result)
            assertFalse(s.isSending)
        }

    @Test
    fun next_rejects_empty_input() =
        runTest(dispatcher) {
            val vm = SendViewModel(FakeLdkService())
            vm.onNextClick()
            assertEquals(SendStep.Input, vm.state.value.step)
            assertNotNull(vm.state.value.validationError)
        }

    @Test
    fun next_rejects_non_numeric_amount() =
        runTest(dispatcher) {
            val vm = SendViewModel(FakeLdkService())
            vm.onInputChange("lnbcsomething")
            // The VM filters non-digits on input, so to simulate a paste that would
            // fail, we bypass via direct state — simplest is to check that amount
            // of "0" (zero) is rejected because parseAmountOrNull requires > 0.
            vm.onAmountSatsChange("0")
            vm.onNextClick()
            // With amount of 0 the ULong parses but is rejected as non-positive.
            assertEquals(SendStep.Input, vm.state.value.step)
            assertNotNull(vm.state.value.validationError)
        }

    @Test
    fun amount_field_strips_non_digits_silently() =
        runTest(dispatcher) {
            val vm = SendViewModel(FakeLdkService())
            vm.onAmountSatsChange("1,234 sats")
            assertEquals("1234", vm.state.value.amountSatsInput)
        }

    @Test
    fun next_advances_to_confirm_and_decodes_best_effort() =
        runTest(dispatcher) {
            val vm = SendViewModel(FakeLdkService())
            vm.onInputChange("lnbc1pfakeinvoicexxx")
            vm.onNextClick()
            // The FakeLdkService's decodeInvoice always succeeds and returns a
            // canned description + amount.
            testScheduler.advanceUntilIdle()
            val s = vm.state.value
            assertEquals(SendStep.Confirm, s.step)
            assertNotNull(s.decodedInvoice)
        }

    @Test
    fun scanned_input_fills_the_field() =
        runTest(dispatcher) {
            val vm = SendViewModel(FakeLdkService())
            vm.applyScanned("   lnbc1pzzz   ")
            assertEquals("lnbc1pzzz", vm.state.value.input)
        }

    @Test
    fun send_flips_to_result_on_success() =
        runTest(dispatcher) {
            val vm = SendViewModel(FakeLdkService())
            vm.onInputChange("bitcoin:bc1qxyz?amount=0.001")
            vm.onNextClick()
            testScheduler.advanceUntilIdle()
            vm.onSendClick()
            testScheduler.advanceUntilIdle()
            val s = vm.state.value
            assertEquals(SendStep.Result, s.step)
            assertNotNull(s.result)
            assertNull(s.sendError)
            assertTrue(s.result is UnifiedSendResult.Bolt11)
        }

    @Test
    fun send_surfaces_error_without_losing_input() =
        runTest(dispatcher) {
            val flaky = FakeLdkService(simulatedError = RuntimeException("no route"))
            val vm = SendViewModel(flaky)
            vm.onInputChange("lnbc1pfake")
            vm.onNextClick()
            testScheduler.advanceUntilIdle()
            vm.onSendClick()
            testScheduler.advanceUntilIdle()
            val s = vm.state.value
            assertEquals(SendStep.Result, s.step)
            assertNull(s.result)
            assertEquals("no route", s.sendError)
        }

    @Test
    fun back_from_confirm_returns_to_input_without_losing_entries() =
        runTest(dispatcher) {
            val vm = SendViewModel(FakeLdkService())
            vm.onInputChange("lnbc1pfake")
            vm.onAmountSatsChange("1500")
            vm.onNextClick()
            testScheduler.advanceUntilIdle()
            vm.onBackToInput()
            val s = vm.state.value
            assertEquals(SendStep.Input, s.step)
            assertEquals("lnbc1pfake", s.input)
            assertEquals("1500", s.amountSatsInput)
        }

    @Test
    fun dismiss_result_resets_to_initial() =
        runTest(dispatcher) {
            val vm = SendViewModel(FakeLdkService())
            vm.onInputChange("lnbc1pfake")
            vm.onNextClick()
            testScheduler.advanceUntilIdle()
            vm.onSendClick()
            testScheduler.advanceUntilIdle()
            vm.onDismissResult()
            val s = vm.state.value
            assertEquals(SendStep.Input, s.step)
            assertTrue(s.input.isEmpty())
            assertNull(s.result)
        }
}
