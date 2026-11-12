package com.laxwifi.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

enum class RingerMode(val label: String) {
    NORMAL("Normal"),
    VIBRATE("Vibrate"),
    SILENT("Silent"),
}

sealed interface WifiAction {
    val id: String

    data class SetRinger(
        override val id: String = newId(),
        val mode: RingerMode = RingerMode.VIBRATE,
    ) : WifiAction

    data class Notify(
        override val id: String = newId(),
        val text: String = "",
    ) : WifiAction

    val summary: String
        get() = when (this) {
            is SetRinger -> mode.label
            is Notify -> "Notify"
        }
}

/**
 * What identifies a Wi-Fi network without its name. Everything here comes from
 * LinkProperties, which needs neither location access nor any LAN traffic.
 */
data class Fingerprint(
    val gateway4: String = "",
    val gateway6: String = "",
    val subnet: String = "",
    val dns: List<String> = emptyList(),
    val domain: String = "",
) {
    /**
     * A long IPv6 link-local gateway is derived from the router's hardware and is
     * close to unique. Short hand-assigned ones (fe80::1, fe80::2) are not.
     */
    val isStrong: Boolean
        get() = gateway6.substringAfter("fe80::", "").length > 4

    val isEmpty: Boolean
        get() = gateway4.isEmpty() && gateway6.isEmpty()
}

enum class Match { NONE, WEAK, STRONG }

fun Fingerprint.matches(current: Fingerprint): Match {
    if (gateway6.isNotEmpty() && current.gateway6.isNotEmpty()) {
        if (gateway6 != current.gateway6) return Match.NONE
        if (isStrong) return Match.STRONG
    }
    if (gateway4.isEmpty() || gateway4 != current.gateway4 || subnet != current.subnet) {
        return Match.NONE
    }
    if (dns.isNotEmpty() && current.dns.isNotEmpty() && dns.none { it in current.dns }) {
        return Match.NONE
    }
    return Match.WEAK
}

data class SavedNetwork(
    val id: String = newId(),
    val name: String,
    val fingerprint: Fingerprint,
    val onConnect: List<WifiAction> = emptyList(),
    val onDisconnect: List<WifiAction> = emptyList(),
)

fun List<SavedNetwork>.bestMatch(current: Fingerprint): SavedNetwork? {
    if (current.isEmpty) return null
    var weak: SavedNetwork? = null
    for (n in this) {
        when (n.fingerprint.matches(current)) {
            Match.STRONG -> return n
            Match.WEAK -> if (weak == null) weak = n
            Match.NONE -> {}
        }
    }
    return weak
}

// ---- JSON ----

private fun WifiAction.toJson(): JSONObject = when (this) {
    is WifiAction.SetRinger -> JSONObject().put("type", "ringer").put("id", id).put("mode", mode.name)
    is WifiAction.Notify -> JSONObject().put("type", "notify").put("id", id).put("text", text)
}

private fun actionFromJson(o: JSONObject): WifiAction? = when (o.optString("type")) {
    "ringer" -> WifiAction.SetRinger(
        id = o.optString("id", newId()),
        mode = runCatching { RingerMode.valueOf(o.optString("mode")) }.getOrDefault(RingerMode.VIBRATE),
    )
    "notify" -> WifiAction.Notify(id = o.optString("id", newId()), text = o.optString("text"))
    else -> null
}

private fun List<WifiAction>.toJson(): JSONArray = JSONArray().also { arr -> forEach { arr.put(it.toJson()) } }

private fun actionsFromJson(arr: JSONArray?): List<WifiAction> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).mapNotNull { actionFromJson(arr.getJSONObject(it)) }
}

private fun Fingerprint.toJson(): JSONObject = JSONObject()
    .put("gateway4", gateway4)
    .put("gateway6", gateway6)
    .put("subnet", subnet)
    .put("dns", JSONArray(dns))
    .put("domain", domain)

private fun fingerprintFromJson(o: JSONObject?): Fingerprint {
    if (o == null) return Fingerprint()
    val dns = o.optJSONArray("dns")
    return Fingerprint(
        gateway4 = o.optString("gateway4"),
        gateway6 = o.optString("gateway6"),
        subnet = o.optString("subnet"),
        dns = if (dns == null) emptyList() else (0 until dns.length()).map { dns.getString(it) },
        domain = o.optString("domain"),
    )
}

fun networksToJson(list: List<SavedNetwork>): String {
    val arr = JSONArray()
    for (n in list) {
        arr.put(
            JSONObject()
                .put("id", n.id)
                .put("name", n.name)
                .put("fingerprint", n.fingerprint.toJson())
                .put("onConnect", n.onConnect.toJson())
                .put("onDisconnect", n.onDisconnect.toJson())
        )
    }
    return arr.toString()
}

fun networksFromJson(json: String?): List<SavedNetwork> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            SavedNetwork(
                id = o.getString("id"),
                name = o.optString("name"),
                fingerprint = fingerprintFromJson(o.optJSONObject("fingerprint")),
                onConnect = actionsFromJson(o.optJSONArray("onConnect")),
                onDisconnect = actionsFromJson(o.optJSONArray("onDisconnect")),
            )
        }
    }.getOrDefault(emptyList())
}
