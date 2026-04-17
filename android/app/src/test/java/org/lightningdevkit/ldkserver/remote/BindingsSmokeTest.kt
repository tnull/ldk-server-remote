package org.lightningdevkit.ldkserver.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lightningdevkit.ldkserver.client.BalanceInfo
import org.lightningdevkit.ldkserver.client.NodeInfo
import org.lightningdevkit.ldkserver.client.PaymentDirection
import org.lightningdevkit.ldkserver.client.PaymentKindInfo
import org.lightningdevkit.ldkserver.client.PaymentStatus
import org.lightningdevkit.ldkserver.client.UnifiedSendResult

/**
 * Smoke test proving the UniFFI-generated Kotlin types actually compile against the
 * app code and their surface looks correct. We construct records + sealed-class
 * variants directly (no JNI calls), which is enough to catch drift if the generator
 * renames a field or changes a signature on future regenerations.
 */
class BindingsSmokeTest {
    @Test
    fun balanceInfo_recordFieldsPresent() {
        val balances =
            BalanceInfo(
                totalOnchainBalanceSats = 1_000UL,
                spendableOnchainBalanceSats = 900UL,
                totalAnchorChannelsReserveSats = 100UL,
                totalLightningBalanceSats = 42UL,
            )
        assertEquals(1_000UL, balances.totalOnchainBalanceSats)
        assertEquals(42UL, balances.totalLightningBalanceSats)
    }

    @Test
    fun nodeInfo_carriesListsAndOptions() {
        val info =
            NodeInfo(
                nodeId = "0211",
                currentBestBlock = null,
                latestLightningWalletSyncTimestamp = 10UL,
                latestOnchainWalletSyncTimestamp = null,
                latestFeeRateCacheUpdateTimestamp = null,
                latestRgsSnapshotTimestamp = null,
                latestNodeAnnouncementBroadcastTimestamp = null,
                listeningAddresses = listOf("127.0.0.1:9735"),
                announcementAddresses = emptyList(),
                nodeAlias = "my-node",
                nodeUris = listOf("0211@example:9735"),
            )
        assertEquals("0211", info.nodeId)
        assertEquals(1, info.listeningAddresses.size)
        assertEquals("my-node", info.nodeAlias)
    }

    @Test
    fun paymentDirection_and_status_enumValuesReachable() {
        // If the generator renamed a variant, this wouldn't compile.
        assertNotNull(PaymentDirection.INBOUND)
        assertNotNull(PaymentDirection.OUTBOUND)
        assertNotNull(PaymentStatus.PENDING)
        assertNotNull(PaymentStatus.SUCCEEDED)
        assertNotNull(PaymentStatus.FAILED)
    }

    @Test
    fun unifiedSendResult_sealedVariantsExist() {
        val results: List<UnifiedSendResult> =
            listOf(
                UnifiedSendResult.Onchain(txid = "tx"),
                UnifiedSendResult.Bolt11(paymentId = "p1"),
                UnifiedSendResult.Bolt12(paymentId = "p2"),
            )
        assertEquals(3, results.size)
        assertTrue(results[0] is UnifiedSendResult.Onchain)
    }

    @Test
    fun paymentKindInfo_allVariantsConstructible() {
        val kinds: List<PaymentKindInfo> =
            listOf(
                PaymentKindInfo.Onchain(txid = "t"),
                PaymentKindInfo.Bolt11(hash = "h", preimage = null),
                PaymentKindInfo.Bolt11Jit(hash = "h", preimage = null),
                PaymentKindInfo.Bolt12Offer(hash = null, preimage = null, offerId = "o"),
                PaymentKindInfo.Bolt12Refund(hash = null, preimage = null),
                PaymentKindInfo.Spontaneous(hash = "h", preimage = null),
            )
        assertEquals(6, kinds.size)
    }
}
