package org.frigate.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerUrlTest {
    @Test
    fun normalizeServerUrlRemovesTrailingSlashes() {
        assertEquals("https://frigate.example", normalizeServerUrl("https://frigate.example///"))
    }

    @Test
    fun normalizeServerUrlPreservesReverseProxyPathAndPort() {
        assertEquals(
            "http://frigate.local:5000/frigate",
            normalizeServerUrl("http://frigate.local:5000/frigate/"),
        )
    }

    @Test
    fun normalizeServerUrlRejectsUnsupportedSchemes() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeServerUrl("file:///etc/passwd")
        }
    }

    @Test
    fun normalizeServerUrlRejectsEmbeddedCredentials() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeServerUrl("https://user:password@frigate.example")
        }
    }
}
