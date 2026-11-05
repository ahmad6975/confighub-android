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
                        TunnelStatus.BLOCKED_BY_KILL_SWITCH -> if (isFa) "مسدود — کیلسوئیچ" else "Blocked — kill switch"
                        TunnelStatus.FAILED -> if (isFa) "اتصال ناموفق" else "Connection failed"
                        else -> if (isFa) "متصل نیست" else "Not connected"
                    }
                )
                // Demo label: remove ONLY when TunnelStatus.CONNECTED is
                // reported by the native sing-box callback (not yet).
                if (!state.isConnected) {
                    Text(if (isFa) "نمایشی — شبیهسازی، نه اتصال واقعی VPN" else "Demo — not a real VPN connection")
                }
            }
            if (state.status == TunnelStatus.TUN_READY) {
                Text(if (isFa) "رابط TUN ساخته شد؛ هسته sing-box هنوز لینک نشده است." else "TUN built; sing-box core not linked yet.")
            }
            if (state.isBlockedByKillSwitch) {
                Text(if (isFa) "تونل قطع شد و کیلسوئیچ روشن است. مسدودسازی واقعی بستهها تا لینک هسته، در انتظار است." else "Tunnel stopped and kill switch is on. Real packet blocking waits for core linking.")
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
                // in-app gate until real packet blocking exists.
                Row(modifier = Modifier.padding(start = 8.dp)) {
                    Text(if (isFa) "کیلسوئیچ" else "Kill switch")
                    Switch(checked = state.killSwitch, onCheckedChange = onKillSwitchChange)
                }
            }
        }
    }
}

/** Panel-style section header: small, bold, no large background. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(12.dp, 8.dp, 12.dp, 2.dp))
}

/** Panel-style country group row: flag, name, live healthy count, counts. */
@Composable
fun CountryRow(
    group: CountryGroup,
    isFa: Boolean,
    onClick: () -> Unit,
) {
    val healthy = group.configs.count { it.isHealthy }
    val best = group.configs.filter { it.isHealthy }.minByOrNull { it.pingMs }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(12.dp, 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("${group.flag} ${if (isFa) group.nameFa else group.nameEn}")
        Text(
            if (best != null) {
                if (isFa) "$healthy از ${group.configs.size} سالم · بهترین ${best.pingMs} ms" else "$healthy of ${group.configs.size} healthy · best ${best.pingMs} ms"
            } else {
                if (isFa) "0 از ${group.configs.size} سالم" else "0 of ${group.configs.size} healthy"
            }
        )
    }
}

/** Config row: flag, name, live ping, connect button. */
@Composable
fun ConfigRow(
    cfg: Config,
    isFa: Boolean,
    onConnect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp, 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(if (isFa) cfg.countryNameFa else cfg.countryNameEn)
            Text("${cfg.host}:${cfg.port} · ${cfg.protocol}")
        }
        Text("${cfg.pingMs} ms", modifier = Modifier.padding(end = 8.dp))
        // Verified connect button: this is the row the update flow
        // has just pinged live; the button itself is a verified stub.
        Button(onClick = onConnect) {
            Text(if (isFa) "اتصال" else "Connect")
        }
    }
}

/** Panel-style "verified & fast" list: real healthy configs, fastest first. */
@Composable
fun VerifiedList(
    configs: List<Config>,
    isFa: Boolean,
    onConnect: (Config) -> Unit,
) {
    Column {
        SectionHeader(if (isFa) "تأییدشده و سریع" else "Verified & fast")
        configs.forEach { cfg ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp, 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (isFa) cfg.countryNameFa else cfg.countryNameEn)
                    Text("${cfg.host}:${cfg.port} · ${cfg.protocol}")
                }
                Text("${cfg.pingMs} ms", modifier = Modifier.padding(end = 8.dp))
                Button(onClick = { onConnect(cfg) }) {
                    Text(if (isFa) "اتصال" else "Connect")
                }
            }
        }
    }
}

/** Small, honest note for the panel: connection is future work. */
@Composable
fun DemoNote(isFa: Boolean) {
    Text(
        if (isFa) "اتصال VPN هنوز در آینده است؛ این فهرست فقط کانفیگهای سالم را نشان میدهد." else "VPN connection is future work; this list only shows healthy configs.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(12.dp),
    )
}
