package org.lightningdevkit.ldkserver.remote.util

import android.net.Uri
import android.util.Base64
import org.lightningdevkit.ldkserver.remote.model.BitcoinNetwork

/**
 * Parse + serialize the `ldk-server-remote:` URI format used by the companion CLI
 * helper (tools/ldk-server-remote-config). The same format is scanned by the Setup
 * screen's QR scanner.
 *
 * Shape:
 * ```
 * ldk-server-remote://setup?v=1
 *                           &name=<url-encoded>
 *                           &network=<mainnet|testnet|signet|regtest>
 *                           &url=<host:port>
 *                           &apikey=<hex>
 *                           &cert=<base64url-encoded PEM, no padding>
 * ```
 *
 * The `//setup` authority is a fixed placeholder — it's not used, but its presence makes
 * the URI hierarchical so Android's `Uri.getQueryParameter(...)` works reliably.
 *
 * `v` is a version marker so we can evolve the format without silently
 * misinterpreting old QR codes.
 */
object ServerUri {
    const val SCHEME = "ldk-server-remote"
    const val CURRENT_VERSION = "1"
    private const val AUTHORITY = "setup"

    /** The scannable fields, matching the five user-editable form inputs. */
    data class Fields(
        val name: String,
        val network: BitcoinNetwork,
        val baseUrl: String,
        val apiKey: String,
        val certificatePem: String,
    )

    sealed class ParseResult {
        data class Success(val fields: Fields) : ParseResult()

        data class Failure(val reason: String) : ParseResult()
    }

    /** Build a QR-ready URI from the five raw fields. */
    fun encode(fields: Fields): String {
        // URL_SAFE|NO_PADDING|NO_WRAP matches the RFC 4648 §5 "base64url" alphabet with no
        // '=' padding and no line wrapping — the same encoding the CLI helper produces.
        val certB64 =
            Base64.encodeToString(
                fields.certificatePem.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
            )
        val builder =
            Uri
                .Builder()
                .scheme(SCHEME)
                .authority(AUTHORITY)
                .appendQueryParameter("v", CURRENT_VERSION)
                .appendQueryParameter("name", fields.name)
                .appendQueryParameter("network", fields.network.displayName())
                .appendQueryParameter("url", fields.baseUrl)
                .appendQueryParameter("apikey", fields.apiKey)
                .appendQueryParameter("cert", certB64)
        return builder.build().toString()
    }

    /** Parse a scanned QR payload. Returns [ParseResult.Failure] on any malformed input. */
    fun decode(text: String): ParseResult {
        val uri =
            runCatching { Uri.parse(text.trim()) }
                .getOrElse { return ParseResult.Failure("not a valid URI") }

        if (uri.scheme != SCHEME) {
            return ParseResult.Failure("unexpected scheme: ${uri.scheme ?: "<missing>"}")
        }

        val version = uri.getQueryParameter("v")
        if (version != CURRENT_VERSION) {
            return ParseResult.Failure(
                if (version == null) {
                    "missing version (v=); can't decode unversioned payload"
                } else {
                    "unsupported payload version: v=$version (expected v=$CURRENT_VERSION)"
                },
            )
        }

        val name =
            uri.getQueryParameter("name")?.takeIf { it.isNotBlank() }
                ?: return ParseResult.Failure("missing `name`")
        val networkRaw =
            uri.getQueryParameter("network")
                ?: return ParseResult.Failure("missing `network`")
        val network =
            parseNetwork(networkRaw)
                ?: return ParseResult.Failure("unknown network: $networkRaw")
        val url =
            uri.getQueryParameter("url")?.takeIf { it.isNotBlank() }
                ?: return ParseResult.Failure("missing `url`")
        val apiKey =
            uri.getQueryParameter("apikey")?.takeIf { it.isNotBlank() }
                ?: return ParseResult.Failure("missing `apikey`")
        val certB64 =
            uri.getQueryParameter("cert")?.takeIf { it.isNotBlank() }
                ?: return ParseResult.Failure("missing `cert`")

        val certPem =
            runCatching {
                val bytes =
                    Base64.decode(
                        certB64,
                        Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
                    )
                String(bytes, Charsets.UTF_8)
            }.getOrElse { return ParseResult.Failure("cert is not valid base64url") }

        if (!certPem.contains("BEGIN CERTIFICATE")) {
            return ParseResult.Failure("decoded cert is not a PEM certificate")
        }

        return ParseResult.Success(
            Fields(
                name = name,
                network = network,
                baseUrl = url,
                apiKey = apiKey,
                certificatePem = certPem,
            ),
        )
    }

    private fun parseNetwork(raw: String): BitcoinNetwork? =
        when (raw.lowercase()) {
            "mainnet" -> BitcoinNetwork.MAINNET
            "testnet" -> BitcoinNetwork.TESTNET
            "signet" -> BitcoinNetwork.SIGNET
            "regtest" -> BitcoinNetwork.REGTEST
            else -> null
        }
}
