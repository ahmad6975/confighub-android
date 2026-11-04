package com.confighub.android.vpn

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.confighub.android.data.Config
import com.confighub.android.data.TunnelStatus

/**
 * Real VpnService wiring — step 2 of the ConfigHub Android app.
 *
 * HONEST LIMIT, READ FIRST: this file now contains a real
 * [ConfigHubVpnService] that Android actually starts, asks the user for
 * VPN permission, and builds a real TUN interface with
 * [VpnService.Builder]. It does NOT yet carry a real VPN tunnel, and
 * it must never be described as one:
 *
 *  - The TUN file descriptor is created, but no packet is forwarded
 *    through an encrypted sing-box outbound yet.
 *  - The ONLY missing link is the native sing-box call, marked TODO
 *    below in [ConfigHubVpnService.startNativeCore]. That call needs
 *    the official open-source sing-box Android library (SagerNet/
 *    sing-box, libbox) built/linked in Android Studio — see
 *    app/build.gradle.kts. No proprietary binary is included here.
 *  - Until that TODO is implemented and returns success, the service
 *    reports [TunnelStatus.TUN_READY], never [TunnelStatus.CONNECTED],
 *    and the UI must keep the "نمایشی" (demo) label.
 *  - Kill switch: the BLOCKED state is reported honestly, but real
 *    packet-level blocking (no traffic outside the tunnel) also
 *    depends on the native core + TUN routing, so it is marked
 *    pending until the native call is linked. See TunnelManager.
 *  - No Psiphon transport exists here (deferred to a future version).
 *  - No server, ID, or config is fabricated: a [Config] only ever
 *    comes from a user-pasted link or the collector.
 */

/** Callback from the service back into the app process. */
fun interface TunnelListener {
    fun onTunnelStatus(status: TunnelStatus, config: Config?)
}

/**
 * Bridge between the service (a separate Android component, same
 * process) and [TunnelManager]. The manager registers itself here;
 * the service reports lifecycle events here. No polling, no fake state.
 */
object TunnelBridge {
    @Volatile
    var listener: TunnelListener? = null

    fun report(status: TunnelStatus, config: Config?) {
        listener?.onTunnelStatus(status, config)
    }
}

class ConfigHubVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.confighub.android.action.START"
        const val ACTION_STOP = "com.confighub.android.action.STOP"
        const val EXTRA_PROTOCOL = "protocol"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_COUNTRY_CODE = "country_code"
        const val EXTRA_COUNTRY_FA = "country_fa"
        const val EXTRA_COUNTRY_EN = "country_en"
        const val EXTRA_PING_MS = "ping_ms"
        const val EXTRA_IS_REALITY = "is_reality"
        const val EXTRA_IS_OBFUSCATED = "is_obfuscated"
        const val EXTRA_KILL_SWITCH = "kill_switch"

        fun startIntent(context: Context, config: Config, killSwitchOn: Boolean): Intent =
            Intent(context, ConfigHubVpnService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROTOCOL, config.protocol)
                putExtra(EXTRA_HOST, config.host)
                putExtra(EXTRA_PORT, config.port)
                putExtra(EXTRA_COUNTRY_CODE, config.countryCode)
                putExtra(EXTRA_COUNTRY_FA, config.countryNameFa)
                putExtra(EXTRA_COUNTRY_EN, config.countryNameEn)
                putExtra(EXTRA_PING_MS, config.pingMs)
                putExtra(EXTRA_IS_REALITY, config.isReality)
                putExtra(EXTRA_IS_OBFUSCATED, config.isObfuscated)
                putExtra(EXTRA_KILL_SWITCH, killSwitchOn)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, ConfigHubVpnService::class.java).apply {
                action = ACTION_STOP
            }
    }

    private var tun: ParcelFileDescriptor? = null
    private var currentConfig: Config? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                closeTun()
                TunnelBridge.report(TunnelStatus.DISCONNECTED, currentConfig)
                stopSelf()
                return Service.START_NOT_STICKY
            }
            ACTION_START -> {
                currentConfig = configFromIntent(intent)
                val config = currentConfig
                if (config == null) {
                    TunnelBridge.report(TunnelStatus.FAILED, null)
                    stopSelf()
                    return Service.START_NOT_STICKY
                }
                // prepare() returns an Intent when user approval is still
                // needed; Android Studio / the system shows the VPN
                // permission dialog then. VpnService.prepare() is called
                // by TunnelManager before starting the service, so by
                // here approval is expected — if establish() still
                // returns null, that is a real failure, not a demo state.
                tun = try {
                    Builder()
                        .setSession("ConfigHub")
                        .addAddress("10.8.0.2", 32)
                        .addRoute("0.0.0.0", 0)
                        .addDnsServer("1.1.1.1")
                        .setMtu(1500)
                        .establish()
                } catch (e: Exception) {
                    null
                }
                if (tun == null) {
                    TunnelBridge.report(TunnelStatus.FAILED, config)
                    stopSelf()
                    return Service.START_NOT_STICKY
                }
                // TUN exists now. This is real service state, but it is
                // NOT a working encrypted tunnel yet.
                TunnelBridge.report(TunnelStatus.TUN_READY, config)
                // TODO (native sing-box, Android Studio linking required):
                //   startNativeCore(tun, config)
                // That function must: build a sing-box outbound JSON from
                // `config` (protocol/host/port/security/reality), hand
                // the TUN fd to the official SagerNet/sing-box libbox
                // core, and ONLY on its success callback report
                // TunnelStatus.CONNECTED. Until then, do not report
                // CONNECTED and do not remove the demo label.
                return Service.START_STICKY
            }
            else -> return Service.START_NOT_STICKY
        }
    }

    override fun onRevoke() {
        // System revoked VPN permission (user removed it, or another
        // VPN took over). This is a real disconnect, not a user tap.
        closeTun()
        TunnelBridge.report(TunnelStatus.DISCONNECTED, currentConfig)
        super.onRevoke()
    }

    override fun onDestroy() {
        closeTun()
        super.onDestroy()
    }

    private fun closeTun() {
        try {
            tun?.close()
        } catch (ignored: Exception) {
            // Closing an already-closed fd is not a failure worth surfacing.
        }
        tun = null
    }

    private fun configFromIntent(intent: Intent): Config? {
        val host = intent.getStringExtra(EXTRA_HOST) ?: return null
        val port = intent.getIntExtra(EXTRA_PORT, -1)
        if (port <= 0) return null
        return Config(
            id = "$host:$port",
            protocol = intent.getStringExtra(EXTRA_PROTOCOL) ?: "vless",
            host = host,
            port = port,
            countryCode = intent.getStringExtra(EXTRA_COUNTRY_CODE) ?: "UNKNOWN",
            countryNameFa = intent.getStringExtra(EXTRA_COUNTRY_FA) ?: "نامشخص",
            countryNameEn = intent.getStringExtra(EXTRA_COUNTRY_EN) ?: "Unknown",
            pingMs = intent.getIntExtra(EXTRA_PING_MS, 0),
            isReality = intent.getBooleanExtra(EXTRA_IS_REALITY, false),
            isObfuscated = intent.getBooleanExtra(EXTRA_IS_OBFUSCATED, false),
        )
    }
}

/**
 * Starts/stops [ConfigHubVpnService] and forwards its real lifecycle
 * events to [TunnelManager] via [TunnelBridge]. It does not invent
 * tunnel state: [isRunning] means the service is running, not that
 * an encrypted tunnel is up. Only [TunnelStatus.CONNECTED] means that,
 * and nothing in this file can report it yet (see TODO above).
 */
class SingBoxTunnel {
    fun isServiceRunning(): Boolean = serviceRunning

    fun start(context: Context, config: Config, killSwitchOn: Boolean): Boolean {
        // User VPN approval: if VpnService.prepare() returns a non-null
        // Intent, the system dialog is still required. TunnelManager
        // checks this first and returns false with a CONNECTING->FAILED
        // path handled there; here we only start when approval exists.
        return try {
            context.startService(ConfigHubVpnService.startIntent(context, config, killSwitchOn))
            serviceRunning = true
            true
        } catch (e: Exception) {
            serviceRunning = false
            false
        }
    }

    fun stop(context: Context) {
        try {
            context.startService(ConfigHubVpnService.stopIntent(context))
        } catch (ignored: Exception) {
            // Service already gone; TunnelManager still reports disconnect.
        }
        serviceRunning = false
    }

    companion object {
        @Volatile
        private var serviceRunning: Boolean = false

        /** Called by TunnelManager when a service event arrives, not by UI. */
        internal fun setServiceRunning(running: Boolean) {
            serviceRunning = running
        }
    }
}
