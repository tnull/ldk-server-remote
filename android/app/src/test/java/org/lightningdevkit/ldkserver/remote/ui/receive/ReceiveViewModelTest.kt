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
import org.lightningdevkit.ldkserver.client.Bolt11ReceiveResult
import org.lightningdevkit.ldkserver.client.ListPaymentsResult
import org.lightningdevkit.ldkserver.client.PageTokenInfo
import org.lightningdevkit.ldkserver.client.PaymentDirection
import org.lightningdevkit.ldkserver.client.PaymentInfo
import org.lightningdevkit.ldkserver.client.PaymentKindInfo
import org.lightningdevkit.ldkserver.client.PaymentStatus
import org.lightningdevkit.ldkserver.remote.service.FakeLdkService
import org.lightningdevkit.ldkserver.remote.service.LdkService

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
            // advanceTimeBy < pollIntervalMillis: generate completes, polling stays
            // suspended at the first delay. advanceUntilIdle would loop forever on
            // the re-scheduled delay because FakeLdkService's canned payments never
            // match the generated hash.
            testScheduler.advanceTimeBy(100L)
            val s = vm.state.value
            assertEquals(ReceiveStep.Qr, s.step)
            assertNotNull(s.generatedPayload)
            assertTrue(
                "expected lightning-like payload, got: ${s.generatedPayload}",
                s.generatedPayload!!.startsWith("lnbc") || s.generatedPayload!!.startsWith("LNBC"),
            )
            vm.reset() // stop the polling before the test scope tears down
        }

    @Test
    fun bolt12_generate_sets_offer_payload() =
        runTest(dispatcher) {
            val vm = ReceiveViewModel(FakeLdkService())
            vm.onTypeChosen(ReceiveType.BOLT12)
            vm.onDescriptionChange("coffee")
            vm.generate()
            testScheduler.advanceTimeBy(100L)
            val payload = vm.state.value.generatedPayload
            assertNotNull(payload)
            assertTrue(payload!!.startsWith("lno"))
            vm.reset()
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
            testScheduler.advanceTimeBy(100L)
            assertEquals(ReceiveStep.Qr, vm.state.value.step)

            vm.onBackFromQr()
            assertEquals(ReceiveStep.Form, vm.state.value.step)
            assertEquals("1234", vm.state.value.amountSatsInput)
            assertEquals("tip", vm.state.value.description)
            assertNull("generated payload should be cleared when returning to the form", vm.state.value.generatedPayload)
            vm.reset()
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

    @Test
    fun bolt11_generate_polls_and_flips_to_received_on_matching_payment() =
        runTest(dispatcher) {
            // Service that returns a canned invoice on receive, and on subsequent
            // listPayments call returns an inbound succeeded BOLT11 with matching hash.
            val matchingPayment =
                PaymentInfo(
                    id = "incoming-1",
                    kind = PaymentKindInfo.Bolt11(hash = "match-me", preimage = "pre"),
                    amountMsat = 5_000_000uL,
                    feePaidMsat = null,
                    direction = PaymentDirection.INBOUND,
                    status = PaymentStatus.SUCCEEDED,
                    latestUpdateTimestamp = 0uL,
                )
            val spy =
                object : LdkService by FakeLdkService() {
                    override suspend fun bolt11Receive(
                        amountMsat: ULong?,
                        description: String?,
                        expirySecs: UInt,
                    ): Bolt11ReceiveResult =
                        Bolt11ReceiveResult(
                            invoice = "lnbcfake",
                            paymentHash = "match-me",
                            paymentSecret = "s",
                        )

                    override suspend fun listPayments(pageToken: PageTokenInfo?) =
                        ListPaymentsResult(
                            payments = listOf(matchingPayment),
                            nextPageToken = null,
                        )
                }

            // Use a tiny poll interval so testScheduler can tick past it quickly.
            val vm = ReceiveViewModel(spy, pollIntervalMillis = 10L)
            vm.onTypeChosen(ReceiveType.BOLT11)
            vm.generate()
            testScheduler.advanceUntilIdle()

            // Generate completes, then the polling coroutine runs its first iteration
            // (after the 10ms delay) and finds the matching payment.
            val s = vm.state.value
            assertEquals(ReceiveStep.Received, s.step)
            assertEquals("incoming-1", s.receivedPayment?.id)
        }

    @Test
    fun bolt12_generate_polls_on_offer_id_not_hash() =
        runTest(dispatcher) {
            val matchingPayment =
                PaymentInfo(
                    id = "bolt12-1",
                    kind = PaymentKindInfo.Bolt12Offer(hash = null, preimage = null, offerId = "target-offer"),
                    amountMsat = 2_000_000uL,
                    feePaidMsat = null,
                    direction = PaymentDirection.INBOUND,
                    status = PaymentStatus.SUCCEEDED,
                    latestUpdateTimestamp = 0uL,
                )
            val spy =
                object : LdkService by FakeLdkService() {
                    override suspend fun bolt12Receive(
                        description: String,
                        amountMsat: ULong?,
                        expirySecs: UInt?,
                        quantity: ULong?,
                    ) = org.lightningdevkit.ldkserver.client.Bolt12ReceiveResult(
                        offer = "lnofake",
                        offerId = "target-offer",
                    )

                    override suspend fun listPayments(pageToken: PageTokenInfo?) =
                        ListPaymentsResult(payments = listOf(matchingPayment), nextPageToken = null)
                }

            val vm = ReceiveViewModel(spy, pollIntervalMillis = 10L)
            vm.onTypeChosen(ReceiveType.BOLT12)
            vm.onDescriptionChange("coffee")
            vm.generate()
            testScheduler.advanceUntilIdle()

            assertEquals(ReceiveStep.Received, vm.state.value.step)
            assertEquals("bolt12-1", vm.state.value.receivedPayment?.id)
        }

    @Test
    fun onchain_does_not_poll() =
        runTest(dispatcher) {
            // Onchain has no matcher → no polling coroutine at all, so advanceUntilIdle
            // terminates quickly (the only pending work is the generate() itself).
            val vm = ReceiveViewModel(FakeLdkService(), pollIntervalMillis = 10L)
            vm.onTypeChosen(ReceiveType.ONCHAIN)
            testScheduler.advanceUntilIdle()
            assertEquals(ReceiveStep.Qr, vm.state.value.step)
            assertNotNull(vm.state.value.generatedPayload)
        }

    @Test
    fun reset_cancels_polling_before_any_match_lands() =
        runTest(dispatcher) {
            // Polling would run forever on the FakeLdkService (non-matching payments),
            // but reset() must stop it cleanly — otherwise the VM keeps hammering the
            // server after the modal closes. We advance bounded time (not
            // advanceUntilIdle, which would spin on the re-scheduled delay forever),
            // then reset, then bounded advance again to give the cancellation a tick
            // to propagate.
            val vm = ReceiveViewModel(FakeLdkService(), pollIntervalMillis = 10L)
            vm.onTypeChosen(ReceiveType.BOLT11)
            vm.generate()
            testScheduler.advanceTimeBy(50L)
            vm.reset()
            testScheduler.advanceTimeBy(100L)
            assertEquals(ReceiveStep.TypePicker, vm.state.value.step)
            assertNull(vm.state.value.receivedPayment)
        }
}
