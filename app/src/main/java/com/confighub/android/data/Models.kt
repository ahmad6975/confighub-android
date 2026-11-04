package com.confighub.android.data

/** A single proxy config. No server, ID or config is fabricated here:
 *  instances are only created from a user-pasted link or a collected
 *  source, exactly like the Python collector. */
data class Config(
    val id: String,
    val protocol: String,
    val host: String,
    val port: Int,
    val countryCode: String, // e.g. "DE", or "UNKNOWN" when there is no marker
    val countryNameFa: String,
    val countryNameEn: String,
    val pingMs: Int,
    val isReality: Boolean = false,
    val isObfuscated: Boolean = false,
    val isStarred: Boolean = false,
) {
    /** Same ceiling as the Python collector and the simulator: 380 ms. */
    val isHealthy: Boolean get() = pingMs <= Collector.HEALTHY_PING_MS
    /** Security rank used only for the "safest" profile: REALITY first. */
    val securityRank: Int get() = when {
        isReality -> 0
        isObfuscated -> 1
        else -> 2
    }
}

data class CountryGroup(
    val code: String,
    val nameFa: String,
    val nameEn: String,
    val flag: String,
    val configs: List<Config>,
) {
    val healthyCount: Int get() = configs.count { it.isHealthy }
}

enum class Profile { FASTEST, SAFEST, FAVORITES, RANDOM }

enum class Language { FA, EN }

/**
 * Real service-lifecycle status, reported by the VpnService itself —
 * not a demo flag. CONNECTED is reachable ONLY from the native
 * sing-box callback (see SingBoxTunnel.kt TODO); until that callback
 * exists, the service can report CONNECTING / TUN_READY / DISCONNECTED /
 * BLOCKED_BY_KILL_SWITCH / FAILED, but never CONNECTED.
 */
enum class TunnelStatus {
    IDLE, CONNECTING, TUN_READY, CONNECTED, DISCONNECTED,
    BLOCKED_BY_KILL_SWITCH, FAILED,
}

data class ConnectionState(
    /** True ONLY when TunnelStatus == CONNECTED (native tunnel up). */
    val isConnected: Boolean = false,
    val activeConfig: Config? = null,
    val status: TunnelStatus = TunnelStatus.IDLE,
    /** True when the VpnService process is actually running. */
    val isServiceRunning: Boolean = false,
    /** Honest demo flag: true until a real sing-box tunnel is linked. */
    val isDemoOnly: Boolean = true,
    val killSwitchOn: Boolean = false,
    val failoverOn: Boolean = false,
    val isBlockedByKillSwitch: Boolean = false,
)
