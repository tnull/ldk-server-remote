package org.lightningdevkit.ldkserver.remote.service

import org.lightningdevkit.ldkserver.client.BalanceInfo
import org.lightningdevkit.ldkserver.client.Bolt11ReceiveResult
import org.lightningdevkit.ldkserver.client.Bolt12ReceiveResult
import org.lightningdevkit.ldkserver.client.ChannelInfo
import org.lightningdevkit.ldkserver.client.DecodedInvoice
import org.lightningdevkit.ldkserver.client.DecodedOffer
import org.lightningdevkit.ldkserver.client.ListPaymentsResult
import org.lightningdevkit.ldkserver.client.NodeInfo
import org.lightningdevkit.ldkserver.client.PageTokenInfo
import org.lightningdevkit.ldkserver.client.PaymentInfo
import org.lightningdevkit.ldkserver.client.PeerInfo
import org.lightningdevkit.ldkserver.client.UnifiedSendResult

/**
 * High-level per-server gateway the UI talks to. Hides the UniFFI-generated client type
 * behind an interface so ViewModels can be unit-tested with an in-memory fake.
 *
 * One `LdkService` instance corresponds to one configured server; switching servers
 * means constructing a new service. That matches how the underlying
 * `LdkServerClientUni` works and keeps per-server TLS / HMAC state cleanly scoped.
 *
 * Every method throws `LdkServerClientException` on failure (from the UniFFI bindings).
 * The service layer intentionally does NOT translate that into a Kotlin-idiomatic
 * Result type — ViewModels catch and translate to UI state themselves, where they have
 * the context to decide whether a failure is retryable, warrants a snackbar, or should
 * surface as an error screen.
 */
interface LdkService {
    // ---- Queries -------------------------------------------------------

    suspend fun getNodeInfo(): NodeInfo

    suspend fun getBalances(): BalanceInfo

    suspend fun listChannels(): List<ChannelInfo>

    suspend fun listPeers(): List<PeerInfo>

    suspend fun listPayments(pageToken: PageTokenInfo? = null): ListPaymentsResult

    suspend fun getPaymentDetails(paymentId: String): PaymentInfo?

    // ---- Receive -------------------------------------------------------

    suspend fun onchainReceive(): String

    suspend fun bolt11Receive(
        amountMsat: ULong?,
        description: String?,
        expirySecs: UInt,
    ): Bolt11ReceiveResult

    suspend fun bolt12Receive(
        description: String,
        amountMsat: ULong?,
        expirySecs: UInt?,
        quantity: ULong?,
    ): Bolt12ReceiveResult

    // ---- Send ----------------------------------------------------------

    suspend fun unifiedSend(
        uri: String,
        amountMsat: ULong?,
    ): UnifiedSendResult

    suspend fun bolt11Send(
        invoice: String,
        amountMsat: ULong?,
    ): String

    suspend fun bolt12Send(
        offer: String,
        amountMsat: ULong?,
        quantity: ULong?,
        payerNote: String?,
    ): String

    suspend fun onchainSend(
        address: String,
        amountSats: ULong?,
        sendAll: Boolean?,
        feeRateSatPerVb: ULong?,
    ): String

    // ---- Channels ------------------------------------------------------

    suspend fun openChannel(
        nodePubkey: String,
        address: String,
        channelAmountSats: ULong,
        pushToCounterpartyMsat: ULong?,
        announceChannel: Boolean,
    ): String

    suspend fun closeChannel(
        userChannelId: String,
        counterpartyNodeId: String,
    )

    suspend fun forceCloseChannel(
        userChannelId: String,
        counterpartyNodeId: String,
        forceCloseReason: String?,
    )

    // ---- Peers ---------------------------------------------------------

    suspend fun connectPeer(
        nodePubkey: String,
        address: String,
        persist: Boolean,
    )

    suspend fun disconnectPeer(nodePubkey: String)

    // ---- Decode --------------------------------------------------------

    suspend fun decodeInvoice(invoice: String): DecodedInvoice

    suspend fun decodeOffer(offer: String): DecodedOffer
}
