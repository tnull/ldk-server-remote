package org.lightningdevkit.ldkserver.remote

import android.app.Application

/**
 * Process-wide entry point. Kept intentionally bare — we do not eagerly construct any
 * `LdkServerClientUni` here: connections are lazy and scoped to the selected server.
 */
class LdkServerRemoteApplication : Application()
