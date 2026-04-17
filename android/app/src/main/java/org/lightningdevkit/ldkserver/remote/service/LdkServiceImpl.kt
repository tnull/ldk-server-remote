package org.lightningdevkit.ldkserver.remote.service

import org.lightningdevkit.ldkserver.client.BalanceInfo
import org.lightningdevkit.ldkserver.client.Bolt11ReceiveResult
import org.lightningdevkit.ldkserver.client.Bolt12ReceiveResult
import org.lightningdevkit.ldkserver.client.ChannelInfo
import org.lightningdevkit.ldkserver.client.DecodedInvoice
import org.lightningdevkit.ldkserver.client.DecodedOffer
import org.lightningdevkit.ldkserver.client.LdkServerClientUni
import org.lightningdevkit.ldkserver.client.ListPaymentsResult
import org.lightningdevkit.ldkserver.client.NodeInfo
import org.lightningdevkit.ldkserver.client.PageTokenInfo
import org.lightningdevkit.ldkserver.client.PaymentInfo
import org.lightningdevkit.ldkserver.client.PeerInfo
import org.lightningdevkit.ldkserver.client.UnifiedSendResult
import org.lightningdevkit.ldkserver.remote.model.ServerEntry

/**
 * Default [LdkService] implementation: thin pass-through to the UniFFI-generated
 * [LdkServerClientUni]. Constructed from a [ServerEntry] — one service per server.
 *
 * No caching or retry logic lives here. The underlying client is `Clone + Send + Sync`
 * on the Rust side (reqwest/hyper clients are internally Arc'd), so holding a single
 * instance for the lifetime of the selected server is cheap and thread-safe.
 */
class LdkServiceImpl(
    private val client: LdkServerClientUni,
) : LdkService {
    constructor(entry: ServerEntry) : this(
        LdkServerClientUni(
            baseUrl = entry.baseUrl,
            apiKey = entry.apiKey,
            serverCertPem = entry.certificatePem,
        ),
    )

    override suspend fun getNodeInfo(): NodeInfo = client.getNodeInfo()

    override suspend fun getBalances(): BalanceInfo = client.getBalances()

    override suspend fun listChannels(): List<ChannelInfo> = client.listChannels()

    override suspend fun listPeers(): List<PeerInfo> = client.listPeers()

    override suspend fun listPayments(pageToken: PageTokenInfo?): ListPaymentsResult = client.listPayments(pageToken)

    override suspend fun getPaymentDetails(paymentId: String): PaymentInfo? = client.getPaymentDetails(paymentId)

    override suspend fun onchainReceive(): String = client.onchainReceive()

    override suspend fun bolt11Receive(
        amountMsat: ULong?,
        description: String?,
        expirySecs: UInt,
    ): Bolt11ReceiveResult = client.bolt11Receive(amountMsat, description, expirySecs)

    override suspend fun bolt12Receive(
        description: String,
        amountMsat: ULong?,
        expirySecs: UInt?,
        quantity: ULong?,
    ): Bolt12ReceiveResult = client.bolt12Receive(description, amountMsat, expirySecs, quantity)

    override suspend fun unifiedSend(
        uri: String,
        amountMsat: ULong?,
    ): UnifiedSendResult = client.unifiedSend(uri, amountMsat)

    override suspend fun bolt11Send(
        invoice: String,
        amountMsat: ULong?,
    ): String = client.bolt11Send(invoice, amountMsat)

    override suspend fun bolt12Send(
        offer: String,
        amountMsat: ULong?,
        quantity: ULong?,
        payerNote: String?,
    ): String = client.bolt12Send(offer, amountMsat, quantity, payerNote)

    override suspend fun onchainSend(
        address: String,
        amountSats: ULong?,
        sendAll: Boolean?,
        feeRateSatPerVb: ULong?,
    ): String = client.onchainSend(address, amountSats, sendAll, feeRateSatPerVb)

    override suspend fun openChannel(
        nodePubkey: String,
        address: String,
        channelAmountSats: ULong,
        pushToCounterpartyMsat: ULong?,
        announceChannel: Boolean,
    ): String =
        client.openChannel(
            nodePubkey,
            address,
            channelAmountSats,
            pushToCounterpartyMsat,
            announceChannel,
        )

    override suspend fun closeChannel(
        userChannelId: String,
        counterpartyNodeId: String,
    ) = client.closeChannel(userChannelId, counterpartyNodeId)

    override suspend fun forceCloseChannel(
        userChannelId: String,
        counterpartyNodeId: String,
        forceCloseReason: String?,
    ) = client.forceCloseChannel(userChannelId, counterpartyNodeId, forceCloseReason)

    override suspend fun connectPeer(
        nodePubkey: String,
        address: String,
        persist: Boolean,
    ) = client.connectPeer(nodePubkey, address, persist)

    override suspend fun disconnectPeer(nodePubkey: String) = client.disconnectPeer(nodePubkey)

    override suspend fun decodeInvoice(invoice: String): DecodedInvoice = client.decodeInvoice(invoice)

    override suspend fun decodeOffer(offer: String): DecodedOffer = client.decodeOffer(offer)
}
