package org.frigate.mobile.data

import java.net.URI
import java.net.URISyntaxException

fun normalizeServerUrl(value: String): String {
    val trimmed = value.trim()
    val uri = try {
        URI(trimmed)
    } catch (exception: URISyntaxException) {
        throw IllegalArgumentException("Enter a valid server URL.", exception)
    }

    require(uri.scheme == "http" || uri.scheme == "https") {
        "Server URL must start with http:// or https://."
    }
    require(!uri.host.isNullOrBlank() && uri.userInfo == null) {
        "Enter a valid server URL without credentials."
    }
    require(uri.query == null && uri.fragment == null) {
        "Server URL cannot include a query or fragment."
    }

    val path = uri.rawPath.orEmpty().trimEnd('/')
    return "${uri.scheme}://${uri.rawAuthority}$path"
}
