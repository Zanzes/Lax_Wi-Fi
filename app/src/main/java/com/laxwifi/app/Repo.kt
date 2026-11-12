package com.laxwifi.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LaxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
    }
}

/** Saved networks, persisted as JSON in SharedPreferences. */
object Repo {
    private lateinit var prefs: SharedPreferences
    private val _networks = MutableStateFlow<List<SavedNetwork>>(emptyList())
    val networks: StateFlow<List<SavedNetwork>> = _networks

    fun init(context: Context) {
        prefs = context.getSharedPreferences("lax_wifi", Context.MODE_PRIVATE)
        _networks.value = networksFromJson(prefs.getString("networks", null))
    }

    /** The saved network whose connect actions ran last and whose disconnect actions are still owed. */
    var activeId: String?
        get() = prefs.getString("active", null)
        set(value) = prefs.edit().putString("active", value).apply()

    fun get(id: String?): SavedNetwork? = _networks.value.firstOrNull { it.id == id }

    fun upsert(network: SavedNetwork) {
        val list = _networks.value
        save(if (list.any { it.id == network.id }) list.map { if (it.id == network.id) network else it } else list + network)
    }

    fun delete(id: String) {
        if (activeId == id) activeId = null
        save(_networks.value.filterNot { it.id == id })
    }

    private fun save(list: List<SavedNetwork>) {
        _networks.value = list
        prefs.edit().putString("networks", networksToJson(list)).apply()
    }
}

data class WifiStatus(val onWifi: Boolean = false, val matchedId: String? = null)

/** Live state published by [MonitorService] for the UI. */
object MonitorState {
    val status = MutableStateFlow(WifiStatus())
}
