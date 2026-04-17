package org.lightningdevkit.ldkserver.remote.ui.node

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.lightningdevkit.ldkserver.remote.service.FakeLdkService
import org.lightningdevkit.ldkserver.remote.service.LdkService

@OptIn(ExperimentalCoroutinesApi::class)
class NodeViewModelTest {
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
    fun initial_load_populates_node_info_and_peers() =
        runTest(dispatcher) {
            val vm = NodeViewModel(FakeLdkService())
            testScheduler.advanceUntilIdle()
            val s = vm.state.value
            assertNotNull(s.nodeInfo)
            assertEquals(1, s.peers.size)
        }

    @Test
    fun connectPeer_surfaces_server_errors() =
        runTest(dispatcher) {
            val flaky =
                object : LdkService by FakeLdkService() {
                    override suspend fun connectPeer(
                        nodePubkey: String,
                        address: String,
                        persist: Boolean,
                    ) = throw RuntimeException("unreachable")
                }
            val vm = NodeViewModel(flaky)
            testScheduler.advanceUntilIdle()
            var result: Result<Unit>? = null
            vm.connectPeer("02abc", "host:9735", true) { result = it }
            testScheduler.advanceUntilIdle()
            assertNotNull(result)
            assertTrue(result!!.isFailure)
        }

    @Test
    fun disconnectPeer_success_refreshes_state() =
        runTest(dispatcher) {
            val vm = NodeViewModel(FakeLdkService())
            testScheduler.advanceUntilIdle()
            var result: Result<Unit>? = null
            vm.disconnectPeer("02counterparty01") { result = it }
            testScheduler.advanceUntilIdle()
            assertNotNull(result)
            assertTrue(result!!.isSuccess)
        }
}
