package org.lightningdevkit.ldkserver.remote.ui.wallet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import org.lightningdevkit.ldkserver.client.BalanceInfo
import org.lightningdevkit.ldkserver.client.PageTokenInfo
import org.lightningdevkit.ldkserver.client.PaymentDirection
import org.lightningdevkit.ldkserver.client.PaymentInfo
import org.lightningdevkit.ldkserver.client.PaymentStatus
import org.lightningdevkit.ldkserver.remote.service.FakeLdkService
import org.lightningdevkit.ldkserver.remote.service.LdkService

@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        // Pin Main to a scheduler-controlled dispatcher so VM `launch`es don't run
        // eagerly — we need to observe the intermediate isRefreshing state.
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initial_load_populates_balances_and_payments() =
        runTest(dispatcher) {
            val vm = WalletViewModel(FakeLdkService())
            testScheduler.advanceUntilIdle()
            val s = vm.state.value
            assertFalse(s.isLoading)
            assertNotNull(s.balances)
            assertEquals(2, s.payments.size)
            assertNull(s.errorMessage)
        }

    @Test
    fun totalSats_is_onchain_plus_lightning() =
        runTest(dispatcher) {
            val vm = WalletViewModel(FakeLdkService())
            testScheduler.advanceUntilIdle()
            val balances = vm.state.value.balances!!
            assertEquals(
                balances.totalOnchainBalanceSats + balances.totalLightningBalanceSats,
                vm.state.value.totalSats,
            )
        }

    @Test
    fun refresh_keeps_previous_data_visible_while_in_flight() =
        runTest(dispatcher) {
            val vm = WalletViewModel(FakeLdkService())
            testScheduler.advanceUntilIdle()
            assertTrue(vm.state.value.hasData)

            vm.refresh(isInitial = false)
            // The synchronous state.update() inside refresh() runs eagerly; the
            // service call is still queued on the scheduler.
            val midFlight = vm.state.value
            assertTrue("mid-flight state should flag refresh in progress", midFlight.isRefreshing)
            assertTrue("old data should remain visible while refreshing", midFlight.hasData)

            testScheduler.advanceUntilIdle()
            assertFalse(vm.state.value.isRefreshing)
        }

    @Test
    fun error_from_service_is_surfaced_and_consumable() =
        runTest(dispatcher) {
            val vm = WalletViewModel(FakeLdkService(simulatedError = RuntimeException("boom")))
            testScheduler.advanceUntilIdle()
            assertNotNull("error should be surfaced", vm.state.value.errorMessage)

            vm.consumeError()
            assertNull(vm.state.value.errorMessage)
        }

    @Test
    fun successful_refresh_after_error_clears_error_and_populates_data() =
        runTest(dispatcher) {
            val real = FakeLdkService()
            val flaky =
                object : LdkService by real {
                    var failOnce = true

                    override suspend fun getBalances(): BalanceInfo {
                        if (failOnce) {
                            failOnce = false
                            throw RuntimeException("transient")
                        }
                        return real.getBalances()
                    }

                    override suspend fun listPayments(pageToken: PageTokenInfo?) = real.listPayments(pageToken)
                }

            val vm = WalletViewModel(flaky)
            testScheduler.advanceUntilIdle()
            assertNotNull(vm.state.value.errorMessage)

            vm.refresh(isInitial = false)
            testScheduler.advanceUntilIdle()
            assertNull("refresh should clear the prior error", vm.state.value.errorMessage)
            assertNotNull(vm.state.value.balances)
        }

    @Test
    fun payment_info_round_trips_from_service() =
        runTest(dispatcher) {
            val vm = WalletViewModel(FakeLdkService())
            testScheduler.advanceUntilIdle()
            val first: PaymentInfo = vm.state.value.payments.first()
            assertEquals(PaymentDirection.OUTBOUND, first.direction)
            assertEquals(PaymentStatus.SUCCEEDED, first.status)
        }

    // Suppresses an unused-import warning when running under the test scope.
    @Suppress("unused")
    private fun useTestScope(): TestScope = throw UnsupportedOperationException()
}
