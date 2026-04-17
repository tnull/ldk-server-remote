package org.lightningdevkit.ldkserver.remote.ui.channels

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
import org.lightningdevkit.ldkserver.client.ChannelInfo
import org.lightningdevkit.ldkserver.client.OutPointInfo
import org.lightningdevkit.ldkserver.remote.service.FakeLdkService
import org.lightningdevkit.ldkserver.remote.service.LdkService

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val sampleChannel =
        ChannelInfo(
            channelId = "c1",
            counterpartyNodeId = "02abcdef",
            fundingTxo = OutPointInfo("tx", 0u),
            userChannelId = "uc-1",
            channelValueSats = 1_000_000uL,
            outboundCapacityMsat = 400_000_000uL,
            inboundCapacityMsat = 600_000_000uL,
            confirmationsRequired = 6u,
            confirmations = 6u,
            isOutbound = true,
            isChannelReady = true,
            isUsable = true,
            isAnnounced = false,
        )

    @Test
    fun initial_load_populates_channels() =
        runTest(dispatcher) {
            val vm = ChannelsViewModel(FakeLdkService())
            testScheduler.advanceUntilIdle()
            assertFalse(vm.state.value.isLoading)
            assertEquals(1, vm.state.value.channels.size)
            assertNull(vm.state.value.errorMessage)
        }

    @Test
    fun refresh_toggles_the_isRefreshing_flag_mid_flight() =
        runTest(dispatcher) {
            val vm = ChannelsViewModel(FakeLdkService())
            testScheduler.advanceUntilIdle()
            vm.refresh()
            assertTrue(vm.state.value.isRefreshing)
            testScheduler.advanceUntilIdle()
            assertFalse(vm.state.value.isRefreshing)
        }

    @Test
    fun openChannel_calls_back_on_success_and_refreshes_the_list() =
        runTest(dispatcher) {
            val vm = ChannelsViewModel(FakeLdkService())
            testScheduler.advanceUntilIdle()

            var result: Result<String>? = null
            vm.openChannel(
                nodePubkey = "02xyz",
                address = "1.2.3.4:9735",
                amountSats = 500_000uL,
                announceChannel = false,
            ) { result = it }
            testScheduler.advanceUntilIdle()
            assertNotNull(result)
            assertTrue(result!!.isSuccess)
        }

    @Test
    fun closeChannel_surfaces_failures_to_the_callback() =
        runTest(dispatcher) {
            val flaky =
                object : LdkService by FakeLdkService() {
                    override suspend fun closeChannel(
                        userChannelId: String,
                        counterpartyNodeId: String,
                    ) = throw RuntimeException("peer offline")
                }
            val vm = ChannelsViewModel(flaky)
            testScheduler.advanceUntilIdle()

            var result: Result<Unit>? = null
            vm.closeChannel(channel = sampleChannel, force = false) { result = it }
            testScheduler.advanceUntilIdle()
            assertNotNull(result)
            assertTrue(result!!.isFailure)
            assertEquals("peer offline", result!!.exceptionOrNull()?.message)
        }

    @Test
    fun forceCloseChannel_passes_reason_to_service() =
        runTest(dispatcher) {
            var capturedReason: String? = "sentinel"
            val spy =
                object : LdkService by FakeLdkService() {
                    override suspend fun forceCloseChannel(
                        userChannelId: String,
                        counterpartyNodeId: String,
                        forceCloseReason: String?,
                    ) {
                        capturedReason = forceCloseReason
                    }
                }
            val vm = ChannelsViewModel(spy)
            testScheduler.advanceUntilIdle()
            vm.closeChannel(channel = sampleChannel, force = true, reason = "peer went dark") {}
            testScheduler.advanceUntilIdle()
            assertEquals("peer went dark", capturedReason)
        }
}
