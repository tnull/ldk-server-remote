package org.lightningdevkit.ldkserver.remote.service

import org.lightningdevkit.ldkserver.client.BalanceInfo
import org.lightningdevkit.ldkserver.client.BestBlockInfo
import org.lightningdevkit.ldkserver.client.Bolt11ReceiveResult
import org.lightningdevkit.ldkserver.client.Bolt12ReceiveResult
import org.lightningdevkit.ldkserver.client.ChannelInfo
import org.lightningdevkit.ldkserver.client.DecodedInvoice
import org.lightningdevkit.ldkserver.client.DecodedOffer
import org.lightningdevkit.ldkserver.client.ListPaymentsResult
import org.lightningdevkit.ldkserver.client.NodeInfo
import org.lightningdevkit.ldkserver.client.OutPointInfo
import org.lightningdevkit.ldkserver.client.PageTokenInfo
import org.lightningdevkit.ldkserver.client.PaymentDirection
import org.lightningdevkit.ldkserver.client.PaymentInfo
import org.lightningdevkit.ldkserver.client.PaymentKindInfo
import org.lightningdevkit.ldkserver.client.PaymentStatus
import org.lightningdevkit.ldkserver.client.PeerInfo
import org.lightningdevkit.ldkserver.client.UnifiedSendResult

/**
 * Deterministic fake [LdkService] for Compose previews and ViewModel unit tests.
 *
 * Does no I/O. Each method returns canned data or echoes back inputs; the seed values
 * are tuned so a wallet UI has something plausible to render.
 *
 * For failure-path testing, construct with `simulatedError = <throwable>` — every call
 * throws that exception.
 */
class FakeLdkService(
    private val simulatedError: Throwable? = null,
) : LdkService {
    private fun <T> check(block: () -> T): T {
        simulatedError?.let { throw it }
        return block()
    }

    override suspend fun getNodeInfo(): NodeInfo =
        check {
            NodeInfo(
                nodeId = "02abf30d19c8f85c1f2af9b27b0a7a00f0a24b1a9c3d4e5f607182930a1b2c3d4e",
                currentBestBlock =
                    BestBlockInfo(
                        blockHash = "0000000000000000000abcdef1234567890abcdef1234567890abcdef12345678",
                        height = 830_000u,
                    ),
                latestLightningWalletSyncTimestamp = 1_700_000_000uL,
                latestOnchainWalletSyncTimestamp = 1_700_000_000uL,
                latestFeeRateCacheUpdateTimestamp = 1_700_000_000uL,
                latestRgsSnapshotTimestamp = null,
                latestNodeAnnouncementBroadcastTimestamp = 1_700_000_000uL,
                listeningAddresses = listOf("127.0.0.1:9735"),
                announcementAddresses = listOf("192.0.2.1:9735"),
                nodeAlias = "sample-node",
                nodeUris = listOf("02abf30d19c8f85c1f2af9b27b0a7a00f0a24b1a9c3d4e5f607182930a1b2c3d4e@192.0.2.1:9735"),
            )
        }

    override suspend fun getBalances(): BalanceInfo =
        check {
            BalanceInfo(
                totalOnchainBalanceSats = 1_000_000uL,
                spendableOnchainBalanceSats = 950_000uL,
                totalAnchorChannelsReserveSats = 50_000uL,
                totalLightningBalanceSats = 2_345_678uL,
            )
        }

    override suspend fun listChannels(): List<ChannelInfo> =
        check {
            listOf(
                ChannelInfo(
                    channelId = "c001",
                    counterpartyNodeId = "02counterparty01",
                    fundingTxo = OutPointInfo(txid = "f1", vout = 0u),
                    userChannelId = "uc-001",
                    channelValueSats = 1_000_000uL,
                    outboundCapacityMsat = 600_000_000uL,
                    inboundCapacityMsat = 400_000_000uL,
                    confirmationsRequired = 6u,
                    confirmations = 6u,
                    isOutbound = true,
                    isChannelReady = true,
                    isUsable = true,
                    isAnnounced = false,
                ),
            )
        }

    override suspend fun listPeers(): List<PeerInfo> =
        check {
            listOf(
                PeerInfo(
                    nodeId = "02counterparty01",
                    address = "192.0.2.2:9735",
                    isPersisted = true,
                    isConnected = true,
                ),
            )
        }

    override suspend fun listPayments(pageToken: PageTokenInfo?): ListPaymentsResult =
        check {
            ListPaymentsResult(
                payments =
                    listOf(
                        PaymentInfo(
                            id = "p-0",
                            kind = PaymentKindInfo.Bolt11(hash = "h-0", preimage = "pre-0"),
                            amountMsat = 1_000_000uL,
                            feePaidMsat = 1_234uL,
                            direction = PaymentDirection.OUTBOUND,
                            status = PaymentStatus.SUCCEEDED,
                            latestUpdateTimestamp = 1_700_000_000uL,
                        ),
                        PaymentInfo(
                            id = "p-1",
                            kind = PaymentKindInfo.Onchain(txid = "tx-1"),
                            amountMsat = 500_000_000uL,
                            feePaidMsat = null,
                            direction = PaymentDirection.INBOUND,
                            status = PaymentStatus.PENDING,
                            latestUpdateTimestamp = 1_699_999_000uL,
                        ),
                    ),
                nextPageToken = null,
            )
        }

    override suspend fun getPaymentDetails(paymentId: String): PaymentInfo? {
        val all = listPayments().payments
        return check { all.firstOrNull { it.id == paymentId } }
    }

    override suspend fun onchainReceive(): String = check { "bcrt1qfakeaddressxxxxxxxxxxxxxxxxxxxxxx" }

    override suspend fun bolt11Receive(
        amountMsat: ULong?,
        description: String?,
        expirySecs: UInt,
    ): Bolt11ReceiveResult =
        check {
            Bolt11ReceiveResult(
                invoice = "lnbc1p00fakeinvoicexxx",
                paymentHash = "ph-$amountMsat",
                paymentSecret = "ps-$amountMsat",
            )
        }

    override suspend fun bolt12Receive(
        description: String,
        amountMsat: ULong?,
        expirySecs: UInt?,
        quantity: ULong?,
    ): Bolt12ReceiveResult =
        check {
            Bolt12ReceiveResult(
                offer = "lno1p00fakeofferxxx",
                offerId = "oid-$amountMsat",
            )
        }

    override suspend fun unifiedSend(
        uri: String,
        amountMsat: ULong?,
    ): UnifiedSendResult =
        check {
            UnifiedSendResult.Bolt11(paymentId = "paid-$uri")
        }

    override suspend fun bolt11Send(
        invoice: String,
        amountMsat: ULong?,
    ): String = check { "b11-paid-$invoice" }

    override suspend fun bolt12Send(
        offer: String,
        amountMsat: ULong?,
        quantity: ULong?,
        payerNote: String?,
    ): String = check { "b12-paid-$offer" }

    override suspend fun onchainSend(
        address: String,
        amountSats: ULong?,
        sendAll: Boolean?,
        feeRateSatPerVb: ULong?,
    ): String = check { "txid-$address" }

    override suspend fun openChannel(
        nodePubkey: String,
        address: String,
        channelAmountSats: ULong,
        pushToCounterpartyMsat: ULong?,
        announceChannel: Boolean,
    ): String = check { "uc-new-$nodePubkey" }

    override suspend fun closeChannel(
        userChannelId: String,
        counterpartyNodeId: String,
    ) = check { Unit }

    override suspend fun forceCloseChannel(
        userChannelId: String,
        counterpartyNodeId: String,
        forceCloseReason: String?,
    ) = check { Unit }

    override suspend fun connectPeer(
        nodePubkey: String,
        address: String,
        persist: Boolean,
    ) = check { Unit }

    override suspend fun disconnectPeer(nodePubkey: String) = check { Unit }

    override suspend fun decodeInvoice(invoice: String): DecodedInvoice =
        check {
            DecodedInvoice(
                destination = "02destination",
                paymentHash = "phash",
                amountMsat = 1_000_000uL,
                timestamp = 1_700_000_000uL,
                expiry = 3_600uL,
                description = "coffee",
                descriptionHash = null,
                fallbackAddress = null,
                minFinalCltvExpiryDelta = 40uL,
                paymentSecret = "psecret",
                currency = "bitcoin",
                paymentMetadata = null,
                isExpired = false,
            )
        }

    override suspend fun decodeOffer(offer: String): DecodedOffer =
        check {
            DecodedOffer(
                offerId = "oid",
                description = "coffee",
                issuer = null,
                amountMsat = 1_000_000uL,
                issuerSigningPubkey = null,
                absoluteExpiry = null,
                chains = listOf("bitcoin"),
                isExpired = false,
            )
        }
}
