package com.confighub.android.vpn

import android.content.Context
import android.net.VpnService
import com.confighub.android.data.Config
import com.confighub.android.data.ConnectionState
import com.confighub.android.data.TunnelStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns connection state for the UI, driven by REAL VpnService
 * lifecycle events — not by a demo flag.
 *
 * Flow (Connect button):
 *   UI -> connect(context, config)
 *     -> VpnService.prepare(context) : null means the user already
 *        approved VPN use; non-null means approval is still needed
 *        and this call returns false (the app must launch that
 *        Intent with startActivityForResult in MainActivity — see
 *        README "VPN permission" — and retry connect afterwards).
 *     -> SingBoxTunnel.start() -> ConfigHubVpnService starts
 *     -> state = CONNECTING, then the service reports TUN_READY
 *        (TUN built) or FAILED (establish() returned null).
 *   CONNECTED is only ever reported by the native sing-box callback
 *   (TODO in SingBoxTunnel.kt). Until then the UI keeps the demo
 *   label, even when TUN_READY is true.
 *
 * Disconnect button:
 *   UI -> disconnect(context) -> service ACTION_STOP
 *     -> state = DISCONNECTED.
 *
 * Kill switch (honest limit):
 *   - setKillSwitch(true) is stored and passed to the service.
 *   - If the tunnel drops WITHOUT an explicit disconnect while
 *     kill switch is on, state becomes BLOCKED_BY_KILL_SWITCH and
 *     isBlockedByKillSwitch = true.
 *   - PENDING REAL PACKET BLOCKING: that blocked state is currently
 *     a UI/service state, not enforced packet blocking. Real
 *     blocking (drop all traffic outside the tunnel) needs the
 *     native sing-box core + TUN routing rules, so it must be
 *     implemented and tested with the native link before the
 *     kill switch is described as "blocking" to the user.
 *
 * Failover rule (from the simulator bug fixes):
 *  - a manual connect turns failover OFF
 *  - only a "best config" connect turns failover ON
 *  - failover must stay inside the originally connected country; it
 *    must never silently jump to another country. (Failover to the
 *    next healthy config in the same country is a later step, once
 *    the native core can report a drop with the country scope.)
 */
class TunnelManager(
    private val tunnel: SingBoxTunnel = SingBoxTunnel(),
) : TunnelListener {

    private val _state = MutableStateFlow(ConnectionState())
    val state: StateFlow<ConnectionState> = _state

    /** Set by disconnect() so a service stop is not mistaken for a drop. */
    private var expectingStop = false
    private var lastCountryCode: String? = null

    init {
        TunnelBridge.listener = this
    }

    /**
     * Returns true if the VPNService was started. True does NOT mean
     * a VPN tunnel is up — watch [state] for TUN_READY / CONNECTED.
     * Returns false if VPN permission is still needed or start failed.
     */
    fun connect(context: Context, config: Config): Boolean {
        return startConnection(context, config, failover = false)
    }

    /** Best-config connect: failover ON, country-scoped (see rule above). */
    fun connectBest(context: Context, config: Config, countryCode: String): Boolean {
        lastCountryCode = countryCode
        return startConnection(context, config, failover = true)
    }

    private fun startConnection(context: Context, config: Config, failover: Boolean): Boolean {
        // Real VPN permission gate. Do not bypass this.
        if (VpnService.prepare(context) != null) {
            _state.value = _state.value.copy(
                status = TunnelStatus.FAILED,
                isConnected = false,
                isServiceRunning = false,
                isDemoOnly = true,
                activeConfig = config,
            )
            return false
        }
        expectingStop = false
        _state.value = ConnectionState(
            isConnected = false,
            activeConfig = config,
            status = TunnelStatus.CONNECTING,
            isServiceRunning = false,
            isDemoOnly = true,
            killSwitchOn = _state.value.killSwitchOn,
            failoverOn = failover,
        )
        val started = tunnel.start(context, config, _state.value.killSwitchOn)
        if (!started) {
            _state.value = _state.value.copy(
                status = TunnelStatus.FAILED, isServiceRunning = false,
            )
        }
        return started
    }

    fun disconnect(context: Context) {
        expectingStop = true
        tunnel.stop(context)
        _state.value = ConnectionState(
            isConnected = false,
            activeConfig = null,
            status = TunnelStatus.DISCONNECTED,
            isServiceRunning = false,
            isDemoOnly = true,
            killSwitchOn = _state.value.killSwitchOn,
            failoverOn = false,
        )
    }

    fun setKillSwitch(on: Boolean) {
        _state.value = _state.value.copy(killSwitchOn = on)
    }

    /** Service lifecycle events, via TunnelBridge. Never called by UI. */
    override fun onTunnelStatus(status: TunnelStatus, config: Config?) {
        val current = _state.value
        when (status) {
            TunnelStatus.TUN_READY -> {
                SingBoxTunnel.setServiceRunning(true)
                _state.value = current.copy(
                    status = TunnelStatus.TUN_READY,
                    isServiceRunning = true,
                    // Still demo: TUN exists, encrypted tunnel does not.
                    isConnected = false, isDemoOnly = true,
                    activeConfig = config ?: current.activeConfig,
                )
            }
            TunnelStatus.CONNECTED -> {
                // Reachable ONLY from the native sing-box success
                // callback (TODO in SingBoxTunnel.kt). If this ever
                // fires before that callback exists, treat it as a bug.
                SingBoxTunnel.setServiceRunning(true)
                _state.value = current.copy(
                    status = TunnelStatus.CONNECTED,
                    isServiceRunning = true,
                    isConnected = true, isDemoOnly = false,
                    activeConfig = config ?: current.activeConfig,
                )
            }
            TunnelStatus.DISCONNECTED -> {
                SingBoxTunnel.setServiceRunning(false)
                if (!expectingStop && current.killSwitchOn && current.activeConfig != null) {
                    // Unexpected drop while kill switch is on.
                    _state.value = current.copy(
                        status = TunnelStatus.BLOCKED_BY_KILL_SWITCH,
                        isServiceRunning = false, isConnected = false,
                        isBlockedByKillSwitch = true,
                    )
                } else {
                    _state.value = current.copy(
                        status = TunnelStatus.DISCONNECTED,
                        isServiceRunning = false, isConnected = false,
                        isBlockedByKillSwitch = false, failoverOn = false,
                    )
                }
                expectingStop = false
            }
            TunnelStatus.FAILED -> {
                SingBoxTunnel.setServiceRunning(false)
                _state.value = current.copy(
                    status = TunnelStatus.FAILED,
                    isServiceRunning = false, isConnected = false,
                    isBlockedByKillSwitch = false,
                )
            }
            else -> {
                _state.value = current.copy(status = status)
            }
        }
    }
}
