package com.confighub.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.confighub.android.data.*

/**
 * Compact top connection card. Deliberately small: status, server name,
 * ping/speed, and a compact connect/disconnect button. No large circle.
 * While the tunnel is a stub, the demo label must stay visible.
 */
@Composable
fun ConnectionCard(
    state: ConnectionState,
    isFa: Boolean,
    onConnectBest: () -> Unit,
    onDisconnect: () -> Unit,
    onKillSwitchChange: (Boolean) -> Unit = {},
) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(12.dp, 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // Real status from the VpnService lifecycle, not a demo flag.
                Text(
                    when (state.status) {
                        TunnelStatus.CONNECTED -> if (isFa) "متصل" else "Connected"
                        TunnelStatus.CONNECTING -> if (isFa) "در حال اتصال…" else "Connecting…"
                        TunnelStatus.TUN_READY -> if (isFa) "سرویس VPN در حال اجراست" else "VPN service running"
                        TunnelStatus.BLOCKED_BY_KILL_SWITCH -> if (isFa) "مسدود — کیل‌سوئیچ" else "Blocked — kill switch"
                        TunnelStatus.FAILED -> if (isFa) "اتصال ناموفق" else "Connection failed"
                        else -> if (isFa) "متصل نیست" else "Not connected"
                    }
                )
                // Demo label: remove ONLY when TunnelStatus.CONNECTED is
                // reported by the native sing-box callback (not yet).
                if (!state.isConnected) {
                    Text(if (isFa) "نمایشی — شبیه‌سازی، نه اتصال واقعی VPN" else "Demo — not a real VPN connection")
                }
            }
            if (state.status == TunnelStatus.TUN_READY) {
                Text(if (isFa) "رابط TUN ساخته شد؛ هسته sing-box هنوز لینک نشده است." else "TUN built; sing-box core not linked yet.")
            }
            if (state.isBlockedByKillSwitch) {
                Text(if (isFa) "تونل قطع شد و کیل‌سوئیچ روشن است. مسدودسازی واقعی بسته‌ها تا لینک هسته، در انتظار است." else "Tunnel dropped with kill switch on. Real packet blocking is pending native linking.")
            }
            state.activeConfig?.let { cfg ->
                Text("${cfg.countryNameFa} — ${cfg.host}:${cfg.port} · ${cfg.pingMs} ms")
            }
            Row {
                if (state.isServiceRunning || state.isConnected) {
                    Button(onClick = onDisconnect, modifier = Modifier.padding(end = 8.dp)) {
                        Text(if (isFa) "قطع" else "Disconnect")
                    }
                } else {
                    // Compact best-connect button for novices, as in the panel.
                    Button(onClick = onConnectBest) {
                        Text(if (isFa) "اتصال به بهترین کانفیگ" else "Connect to best config")
                    }
                }
                // Tiny kill-switch toggle, kept on the same card so the
                // page does not gain a new section. It only changes the
                // reported blocked state until native blocking is linked.
                Row(modifier = Modifier.padding(start = 8.dp)) {
                    Text(if (isFa) "کیل‌سوئیچ" else "Kill switch")
                    Switch(
                        checked = state.killSwitchOn,
                        onCheckedChange = onKillSwitchChange,
                    )
                }
            }
        }
    }
}

/** One country group, collapsed by default so the page never crowds. */
@Composable
fun CountryGroupCard(
    group: CountryGroup,
    isFa: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onConnect: (Config) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp, 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle() },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${group.flag} ${if (isFa) group.nameFa else group.nameEn}")
                Text(
                    if (isFa) "${group.healthyCount} کانفیگ سالم"
                    else "${group.healthyCount} healthy configs"
                )
            }
            if (expanded) {
                group.configs.forEach { cfg ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${cfg.protocol} · ${cfg.host}:${cfg.port} · ${cfg.pingMs} ms")
                        Row {
                            // Small REALITY / obfuscation badge, never guessed:
                            // only set when the link itself carries it.
                            if (cfg.isReality) Text("REALITY ")
                            else if (cfg.isObfuscated) Text(if (isFa) "استتار " else "Obfuscated ")
                            // Dead rows must not silently switch country:
                            // the button is disabled over the 380 ms ceiling.
                            Button(
                                onClick = { onConnect(cfg) },
                                enabled = cfg.isHealthy,
                            ) {
                                Text(if (isFa) "اتصال" else "Connect")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Support section at the bottom. No fabricated ID: empty means "not set yet". */
@Composable
fun SupportCard(supportId: String?, isFa: Boolean) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(12.dp, 8.dp)) {
            Text(if (isFa) "پشتیبانی" else "Support")
            Text(supportId ?: if (isFa) "هنوز تنظیم نشده است" else "Not set yet")
        }
    }
}
