package org.lightningdevkit.ldkserver.remote.ui.navigation

/**
 * Top-level navigation routes. Using string routes (rather than Nav-Compose 2.8's
 * type-safe DSL) deliberately — it keeps the integration with Compose previews and
 * instrumented tests simpler and lets us pass optional arguments via query strings.
 */
object Routes {
    const val SERVER_LIST = "serverList"

    /** Add server screen. Path arg: optional existing entry id to edit. */
    const val ADD_SERVER_PATTERN = "addServer?editId={editId}"

    fun addServer(editId: String? = null): String = if (editId == null) "addServer" else "addServer?editId=$editId"

    /** Tabs for a specific server. Required path arg: serverId. */
    const val MAIN_PATTERN = "main/{serverId}"

    fun main(serverId: String): String = "main/$serverId"

    // Tab routes inside the MainScaffold.
    object Tab {
        const val WALLET = "wallet"
        const val CHANNELS = "channels"
        const val NODE = "node"
    }
}
