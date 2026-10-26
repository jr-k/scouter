package app.scouter

import android.content.Context
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.UUID

object ScouterNetworkConfig {
    const val DEFAULT_PORT = 8765
    const val DEFAULT_BIND_HOST = "0.0.0.0"

    data class NetworkSettings(
        val enabled: Boolean,
        val bindHost: String,
        val port: Int,
    )

    fun settings(context: Context): NetworkSettings {
        val preferences = preferences(context)
        return NetworkSettings(
            enabled = preferences.getBoolean("enabled", true),
            bindHost = preferences.getString("bind_host", DEFAULT_BIND_HOST) ?: DEFAULT_BIND_HOST,
            port = preferences.getInt("port", DEFAULT_PORT),
        )
    }

    fun save(context: Context, enabled: Boolean, bindHost: String, port: Int): NetworkSettings {
        require(port in 1024..65535) { "Port must be between 1024 and 65535" }
        val address = InetAddress.getByName(bindHost.trim())
        require(address is Inet4Address) { "Bind host must be an IPv4 address" }
        val value = NetworkSettings(enabled, address.hostAddress ?: bindHost.trim(), port)
        preferences(context).edit()
            .putBoolean("enabled", value.enabled)
            .putString("bind_host", value.bindHost)
            .putInt("port", value.port)
            .apply()
        return value
    }

    fun token(context: Context): String {
        val preferences = preferences(context)
        return preferences.getString("token", null) ?: UUID.randomUUID().toString()
            .replace("-", "")
            .also { preferences.edit().putString("token", it).apply() }
    }

    fun regenerateToken(context: Context): String =
        UUID.randomUUID().toString().replace("-", "").also {
            preferences(context).edit().putString("token", it).apply()
        }

    fun localAddress(context: Context): String {
        val settings = settings(context)
        val address = if (settings.bindHost == DEFAULT_BIND_HOST) {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp }
                .flatMap { networkInterface ->
                    networkInterface.inetAddresses.toList()
                        .filterIsInstance<Inet4Address>()
                        .filterNot { it.isLoopbackAddress }
                        .map { networkInterface.name to it }
                }
                .sortedBy { (interfaceName, address) ->
                    when {
                        address.isTailscaleAddress() && interfaceName.startsWith("tun") -> 0
                        address.isTailscaleAddress() -> 1
                        address.isSiteLocalAddress -> 2
                        else -> 3
                    }
                }
                .firstOrNull()
                ?.second
                ?.hostAddress
                ?: "127.0.0.1"
        } else {
            settings.bindHost
        }
        return "http://$address:${settings.port}"
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences("scouter_network", Context.MODE_PRIVATE)

    private fun Inet4Address.isTailscaleAddress(): Boolean {
        val octets = address.map { it.toInt() and 0xff }
        return octets[0] == 100 && octets[1] in 64..127
    }
}
