package org.lightningdevkit.ldkserver.remote.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.lightningdevkit.ldkserver.client.BalanceInfo
import org.lightningdevkit.ldkserver.client.PaymentInfo
import org.lightningdevkit.ldkserver.remote.service.LdkService

/**
 * State container for the Wallet tab. Fetches balances + the first page of payments
 * from the [LdkService] on init and on [refresh]. Keeps the old data visible while a
 * refresh is in flight — the Bitcoin Design Guide calls for unobtrusive sync —
 * and surfaces transient failures as a separate [errorMessage] so the screen can
 * render a snackbar without blowing away the last-known state.
 */
class WalletViewModel(
    private val service: LdkService,
) : ViewModel() {
    private val mutableState = MutableStateFlow(WalletUiState())
    val state: StateFlow<WalletUiState> = mutableState.asStateFlow()

    init {
        refresh(isInitial = true)
    }

    fun refresh(isInitial: Boolean = false) {
        mutableState.update {
            it.copy(
                isLoading = isInitial,
                isRefreshing = !isInitial,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            val balancesResult = runCatching { service.getBalances() }
            val paymentsResult = runCatching { service.listPayments(pageToken = null) }

            mutableState.update { prev ->
                val balances = balancesResult.getOrNull() ?: prev.balances
                val payments =
                    paymentsResult
                        .getOrNull()
                        ?.payments
                        ?: prev.payments
                val failure =
                    paymentsResult.exceptionOrNull()
                        ?: balancesResult.exceptionOrNull()
                prev.copy(
                    isLoading = false,
                    isRefreshing = false,
                    balances = balances,
                    payments = payments,
                    errorMessage = failure?.friendlyMessage(),
                )
            }
        }
    }

    /** After the caller has shown the error to the user, clear it so it doesn't re-fire. */
    fun consumeError() {
        mutableState.update { it.copy(errorMessage = null) }
    }
}

/**
 * Merged render state for the wallet screen.
 *
 * * [isLoading] is true only on the very first fetch — it drives the full-screen
 *   spinner.
 * * [isRefreshing] is true while a pull-to-refresh is in flight; the screen shows
 *   the indicator overlay but keeps rendering the last-known values.
 * * [errorMessage], when non-null, should be surfaced via a transient snackbar and
 *   then cleared through [WalletViewModel.consumeError].
 */
data class WalletUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val balances: BalanceInfo? = null,
    val payments: List<PaymentInfo> = emptyList(),
    val errorMessage: String? = null,
) {
    val hasData: Boolean get() = balances != null

    val totalSats: ULong get() = (balances?.totalOnchainBalanceSats ?: 0UL) + (balances?.totalLightningBalanceSats ?: 0UL)
}

private fun Throwable.friendlyMessage(): String {
    val raw = message ?: this::class.simpleName ?: "Unknown error"
    // UniFFI-generated errors subclass Throwable with the `reason` field we control.
    // If it's one of those, the toString already reads "Invalid request: <reason>",
    // which is exactly what we want to show. For other throwables (e.g. cert parse
    // errors at construction time), fall back to the message.
    return raw
}
