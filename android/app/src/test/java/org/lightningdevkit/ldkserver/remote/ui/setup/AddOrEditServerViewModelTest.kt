package org.lightningdevkit.ldkserver.remote.ui.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lightningdevkit.ldkserver.remote.model.BitcoinNetwork
import org.lightningdevkit.ldkserver.remote.model.InMemoryServerStore
import org.lightningdevkit.ldkserver.remote.model.ServerEntry
import org.lightningdevkit.ldkserver.remote.util.ServerUri

class AddOrEditServerViewModelTest {
    private val validPem = "-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE-----"

    @Test
    fun save_rejects_empty_fields() {
        val store = InMemoryServerStore()
        val vm = AddOrEditServerViewModel(store = store, editId = null, clock = { 0L })
        assertFalse(vm.save())
        assertEquals("Name is required", vm.state.validationError)
        assertTrue(store.servers.value.isEmpty())
    }

    @Test
    fun save_rejects_baseUrl_with_scheme() {
        val store = InMemoryServerStore()
        val vm = AddOrEditServerViewModel(store = store, editId = null, clock = { 0L })
        vm.onNameChange("node")
        vm.onBaseUrlChange("https://node.example:3000")
        vm.onApiKeyChange("abc")
        vm.onCertificatePemChange(validPem)
        assertFalse(vm.save())
        assertNotNull(vm.state.validationError)
        assertTrue(vm.state.validationError!!.contains("scheme"))
    }

    @Test
    fun save_rejects_non_PEM_certificate() {
        val store = InMemoryServerStore()
        val vm = AddOrEditServerViewModel(store = store, editId = null, clock = { 0L })
        vm.onNameChange("node")
        vm.onBaseUrlChange("x:1")
        vm.onApiKeyChange("abc")
        vm.onCertificatePemChange("just some garbage")
        assertFalse(vm.save())
        assertTrue(vm.state.validationError!!.contains("PEM"))
    }

    @Test
    fun save_persists_new_entry_when_valid() {
        val store = InMemoryServerStore()
        val vm = AddOrEditServerViewModel(store = store, editId = null, clock = { 42L })
        vm.onNameChange("  padded name  ")
        vm.onBaseUrlChange("host.example:3000")
        vm.onApiKeyChange("deadbeef")
        vm.onCertificatePemChange(validPem)
        vm.onNetworkChange(BitcoinNetwork.MAINNET)

        assertTrue(vm.save())
        assertEquals(1, store.servers.value.size)
        val saved = store.servers.value.single()
        assertEquals("padded name", saved.name)
        assertEquals(BitcoinNetwork.MAINNET, saved.network)
        assertEquals("host.example:3000", saved.baseUrl)
        assertEquals(42L, saved.createdAtEpochSeconds)
    }

    @Test
    fun save_updates_existing_entry_without_replacing_id() {
        val existing =
            ServerEntry(
                id = "abc",
                name = "orig",
                network = BitcoinNetwork.SIGNET,
                baseUrl = "x:1",
                apiKey = "k",
                certificatePem = validPem,
                createdAtEpochSeconds = 100L,
            )
        val store = InMemoryServerStore(initial = listOf(existing))
        val vm = AddOrEditServerViewModel(store = store, editId = "abc", clock = { 999L })

        vm.onNameChange("renamed")
        assertTrue(vm.save())

        val after = store.get("abc")!!
        assertEquals("renamed", after.name)
        assertEquals("abc", after.id)
        assertEquals(100L, after.createdAtEpochSeconds) // createdAt preserved
    }

    @Test
    fun applyScanned_prefills_every_field_and_preserves_editability() {
        val store = InMemoryServerStore()
        val vm = AddOrEditServerViewModel(store = store, editId = null, clock = { 0L })

        val fields =
            ServerUri.Fields(
                name = "scanned",
                network = BitcoinNetwork.REGTEST,
                baseUrl = "192.168.1.5:3000",
                apiKey = "cafef00d",
                certificatePem = validPem,
            )
        vm.applyScanned(fields)

        assertEquals("scanned", vm.state.name)
        assertEquals(BitcoinNetwork.REGTEST, vm.state.network)
        assertEquals("192.168.1.5:3000", vm.state.baseUrl)
        assertNotNull(vm.state.scanInfo)
        assertNull(vm.state.validationError)

        // User must be able to still edit after scanning.
        vm.onBaseUrlChange("192.168.1.7:3000")
        assertEquals("192.168.1.7:3000", vm.state.baseUrl)
    }

    @Test
    fun onScanError_does_not_clobber_form() {
        val store = InMemoryServerStore()
        val vm = AddOrEditServerViewModel(store = store, editId = null, clock = { 0L })
        vm.onNameChange("typed by user")

        vm.onScanError("unexpected scheme")
        assertEquals("typed by user", vm.state.name)
        assertNotNull(vm.state.scanInfo)
    }
}
