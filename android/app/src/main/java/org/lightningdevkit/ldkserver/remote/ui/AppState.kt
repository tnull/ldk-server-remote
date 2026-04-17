package org.lightningdevkit.ldkserver.remote.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.StateFlow
import org.lightningdevkit.ldkserver.remote.model.EncryptedServerStore
import org.lightningdevkit.ldkserver.remote.model.ServerEntry
import org.lightningdevkit.ldkserver.remote.model.ServerStore
import org.lightningdevkit.ldkserver.remote.service.LdkService
import org.lightningdevkit.ldkserver.remote.service.LdkServiceImpl

/**
 * Process-wide app state. Holds the [ServerStore] (source of truth for configured
 * servers) and a factory that constructs a per-server [LdkService] on demand.
 *
 * There's no "selected server" field here — selection lives in the navigation graph
 * (via the `serverId` path argument on the `main/...` route). Screens that need a
 * service ask `AppState.serviceFor(serverId)` and get back a short-lived instance
 * they can use for that navigation entry's lifetime.
 */
open class AppState(
    val serverStore: ServerStore,
    private val serviceFactory: (ServerEntry) -> LdkService = { LdkServiceImpl(it) },
) : ViewModel() {
    val servers: StateFlow<List<ServerEntry>> = serverStore.servers

    /** Build (or return a cached) [LdkService] for the server with the given id. */
    fun serviceFor(serverId: String): LdkService? {
        val entry = serverStore.get(serverId) ?: return null
        return cache.getOrPut(serverId) { serviceFactory(entry) }
    }

    /** Drop any cached service for [serverId], e.g. after credentials change. */
    fun invalidateService(serverId: String) {
        cache.remove(serverId)
    }

    private val cache = HashMap<String, LdkService>()

    companion object {
        val Factory: ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T {
                    val app =
                        extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                            as Application
                    @Suppress("UNCHECKED_CAST")
                    return AppState(serverStore = EncryptedServerStore.create(app)) as T
                }
            }
    }
}

/**
 * Subclass used for testing — accepts an arbitrary [ServerStore] + service factory.
 * Needed because the production factory constructs the EncryptedServerStore from
 * the Application context, which is awkward to mock in unit tests.
 */
class TestAppState(
    serverStore: ServerStore,
    serviceFactory: (ServerEntry) -> LdkService,
) : AppState(serverStore, serviceFactory)

// Suppressed: lint wants Application-subclass here to avoid leaking Context, but the
// actual AppState holds only a ServerStore (not Context). The Factory above lives as
// a companion object for the production path.
@Suppress("unused")
private class AppStateLintStub(app: Application) : AndroidViewModel(app)
