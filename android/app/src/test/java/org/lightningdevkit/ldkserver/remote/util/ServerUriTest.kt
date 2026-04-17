package org.lightningdevkit.ldkserver.remote.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.lightningdevkit.ldkserver.remote.model.BitcoinNetwork
import org.robolectric.RobolectricTestRunner

/**
 * Round-trip + malformed-input coverage for the `ldk-server-remote:` URI format.
 *
 * Runs under Robolectric because encode/decode both call into `android.net.Uri` and
 * `android.util.Base64`, which aren't in plain JDK.
 */
@RunWith(RobolectricTestRunner::class)
class ServerUriTest {
    private val samplePem =
        """
        -----BEGIN CERTIFICATE-----
        MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxx...
        -----END CERTIFICATE-----
        """.trimIndent()

    private val sample =
        ServerUri.Fields(
            name = "home signet",
            network = BitcoinNetwork.SIGNET,
            baseUrl = "192.168.1.5:3000",
            apiKey = "deadbeef1234",
            certificatePem = samplePem,
        )

    @Test
    fun encode_then_decode_yields_equal_fields() {
        val uri = ServerUri.encode(sample)
        val decoded = ServerUri.decode(uri)
        val success = decoded as ServerUri.ParseResult.Success
        assertEquals(sample, success.fields)
    }

    @Test
    fun encoded_uri_starts_with_expected_scheme() {
        val uri = ServerUri.encode(sample)
        assertTrue("expected ldk-server-remote scheme, got: $uri", uri.startsWith("ldk-server-remote://setup?"))
        // version marker is always first-class
        assertTrue("expected v=1 in uri: $uri", uri.contains("v=1"))
    }

    @Test
    fun encode_handles_special_chars_in_name() {
        val weird = sample.copy(name = "my node & friends")
        val decoded = ServerUri.decode(ServerUri.encode(weird))
        assertEquals(
            "my node & friends",
            (decoded as ServerUri.ParseResult.Success).fields.name,
        )
    }

    @Test
    fun decode_rejects_wrong_scheme() {
        val result = ServerUri.decode("https://example.com/?v=1&name=x")
        assertTrue(result is ServerUri.ParseResult.Failure)
    }

    @Test
    fun decode_rejects_unknown_version() {
        val uri = ServerUri.encode(sample).replace("v=1", "v=99")
        val result = ServerUri.decode(uri)
        assertTrue("expected failure on v=99, got: $result", result is ServerUri.ParseResult.Failure)
        assertTrue(
            "failure reason should mention version",
            (result as ServerUri.ParseResult.Failure).reason.contains("version"),
        )
    }

    @Test
    fun decode_rejects_missing_version() {
        val result = ServerUri.decode("ldk-server-remote://setup?name=x&network=signet&url=x&apikey=x&cert=x")
        assertTrue(result is ServerUri.ParseResult.Failure)
    }

    @Test
    fun decode_rejects_unknown_network() {
        val uri = ServerUri.encode(sample).replace("network=signet", "network=bogus")
        val result = ServerUri.decode(uri)
        assertTrue("expected failure on unknown network, got: $result", result is ServerUri.ParseResult.Failure)
    }

    @Test
    fun decode_trims_whitespace() {
        val uri = "\n  ${ServerUri.encode(sample)}  \t"
        val result = ServerUri.decode(uri)
        assertTrue(result is ServerUri.ParseResult.Success)
    }

    @Test
    fun decode_rejects_cert_that_isnt_a_pem() {
        val uri = ServerUri.encode(sample.copy(certificatePem = "not a cert at all"))
        val result = ServerUri.decode(uri)
        assertTrue("expected failure on non-PEM cert, got: $result", result is ServerUri.ParseResult.Failure)
    }

    @Test
    fun decode_rejects_garbage() {
        assertTrue(ServerUri.decode("garbage!!!") is ServerUri.ParseResult.Failure)
        assertTrue(ServerUri.decode("") is ServerUri.ParseResult.Failure)
    }

    @Test
    fun all_four_networks_round_trip() {
        BitcoinNetwork.entries.forEach { net ->
            val fields = sample.copy(network = net)
            val decoded = ServerUri.decode(ServerUri.encode(fields))
            assertEquals(net, (decoded as ServerUri.ParseResult.Success).fields.network)
        }
    }
}
