package org.lightningdevkit.ldkserver.remote.ui.receive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.lightningdevkit.ldkserver.remote.service.FakeLdkService

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiveViewModelTest {
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
    fun starts_in_type_picker() =
        runTest(dispatcher) {
            val vm = ReceiveViewModel(FakeLdkService())
            assertEquals(ReceiveStep.TypePicker, vm.state.value.step)
            assertNull(vm.state.value.type)
        }

    @Test
    fun picking_onchain_generates_and_jumps_to_qr() =
        runTest(dispatcher) {
            val vm = ReceiveViewModel(FakeLdkService())
            vm.onTypeChosen(ReceiveType.ONCHAIN)
            // Onchain has no parameters — should skip the form and fire generate().
            assertEquals(ReceiveStep.Qr, vm.state.value.step)
            testScheduler.advanceUntilIdle()
            assertNotNull(vm.state.value.generatedPayload)
        }

    @Test
    fun picking_bolt11_goes_to_form_not_qr() =
        runTest(dispatcher) {
            val vm = ReceiveViewModel(FakeLdkService())
            vm.onTypeChosen(ReceiveType.BOLT11)
            assertEquals(ReceiveStep.Form, vm.state.value.step)
            assertNull(vm.state.value.generatedPayload)
        }

    @Test
    fun amount_strips_non_digits() =
        runTest(dispatcher) {
            val vm = ReceiveViewModel(FakeLdkService())
            vm.onAmountSatsChange("1,234 sats")
            assertEquals("1234", vm.state.value.amountSatsInput)
        }

    @Test
    fun bolt11_generate_sets_invoice_payload() =
        runTest(dispatcher) {
            val vm = ReceiveViewModel(FakeLdkService())
            vm.onTypeChosen(ReceiveType.BOLT11)
            vm.onAmountSatsChange("1000")
            vm.generate()
            testScheduler.advanceUntilIdle()
            val s = vm.state.value
            assertEquals(ReceiveStep.Qr, s.step)
            assertNotNull(s.generatedPayload)
            assertTrue(
                "expected lightning-like payload, got: ${s.generatedPayload}",
                s.generatedPayload!!.startsWith("lnbc") || s.generatedPayload!!.startsWith("LNBC"),
            )
        }

    @Test
    fun bolt12_generate_sets_offer_payload() =
        runTest(dispatcher) {
            val vm = ReceiveViewModel(FakeLdkService())
            vm.onTypeChosen(ReceiveType.BOLT12)
            vm.onDescriptionChange("coffee")
            vm.generate()
            testScheduler.advanceUntilIdle()
            val payload = vm.state.value.generatedPayload
            assertNotNull(payload)
            assertTrue(payload!!.startsWith("lno"))
        }

    @Test
    fun generation_error_is_surfaced_and_retryable() =
        runTest(dispatcher) {
            val flaky = FakeLdkService(simulatedError = RuntimeException("no liquidity"))
            val vm = ReceiveViewModel(flaky)
            vm.onTypeChosen(ReceiveType.ONCHAIN)
            testScheduler.advanceUntilIdle()
            assertEquals("no liquidity", vm.state.value.error)
            assertNull(vm.state.value.generatedPayload)
        }

    @Test
    fun back_from_form_returns_to_type_picker() =
        runTest(dispatcher) {
            val vm = ReceiveViewModel(FakeLdkService())
            vm.onTypeChosen(ReceiveType.BOLT11)
            vm.onBackFromForm()
            assertEquals(ReceiveStep.TypePicker, vm.state.value.step)
        }

    @Test
    fun back_from_qr_for_bolt11_returns_to_form_with_inputs_preserved() =
        runTest(dispatcher) {
            val vm = ReceiveViewModel(FakeLdkService())
            vm.onTypeChosen(ReceiveType.BOLT11)
            vm.onAmountSatsChange("1234")
            vm.onDescriptionChange("tip")
            vm.generate()
            testScheduler.advanceUntilIdle()
            assertEquals(ReceiveStep.Qr, vm.state.value.step)

            vm.onBackFromQr()
            assertEquals(ReceiveStep.Form, vm.state.value.step)
            assertEquals("1234", vm.state.value.amountSatsInput)
            assertEquals("tip", vm.state.value.description)
            assertNull("generated payload should be cleared when returning to the form", vm.state.value.generatedPayload)
        }

    @Test
    fun back_from_qr_for_onchain_returns_to_type_picker() =
        runTest(dispatcher) {
            val vm = ReceiveViewModel(FakeLdkService())
            vm.onTypeChosen(ReceiveType.ONCHAIN)
            testScheduler.advanceUntilIdle()
            vm.onBackFromQr()
            assertEquals(ReceiveStep.TypePicker, vm.state.value.step)
        }

    @Test
    fun reset_wipes_the_whole_state() =
        runTest(dispatcher) {
            val vm = ReceiveViewModel(FakeLdkService())
            vm.onTypeChosen(ReceiveType.BOLT11)
            vm.onAmountSatsChange("500")
            vm.reset()
            val s = vm.state.value
            assertEquals(ReceiveStep.TypePicker, s.step)
            assertNull(s.type)
            assertTrue(s.amountSatsInput.isEmpty())
        }
}
