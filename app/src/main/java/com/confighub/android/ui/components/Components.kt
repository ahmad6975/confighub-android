package com.confighub.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.confighub.android.data.*
import com.confighub.android.ui.theme.AccentGreen
import com.confighub.android.ui.theme.AccentTeal
import com.confighub.android.ui.theme.DarkBlue
import com.confighub.android.ui.theme.DarkGreen
import com.confighub.android.ui.theme.PanelText

private val CardShape = RoundedCornerShape(20.dp)
private val PanelBrush = Brush.verticalGradient(listOf(DarkBlue, DarkGreen))

/**
 * Compact top connection card, now glassy: dark blue-to-green gradient,
 * rounded corners, a coloured status dot, and a full-width teal button.
 * Still deliberately small — no large circle. While the tunnel is a stub,
 * the demo label must stay visible. Real connection stays future work.
 */
@Composable
fun ConnectionCard(
    state: ConnectionState,
    isFa: Boolean,
    onConnectBest: () -> Unit,
    onDisconnect: () -> Unit,
    onKillSwitchChange: (Boolean) -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp, 8.dp),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.background(PanelBrush).padding(16.dp, 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(10.dp).clip(CircleShape)
                            .background(if (state.isConnected) AccentGreen else Color(0xFFFFB020))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        when (state.status) {
                            TunnelStatus.CONNECTED -> if (isFa) "متصل" else "Connected"
                            TunnelStatus.CONNECTING -> if (isFa) "در حال اتصال…" else "Connecting…"
                            TunnelStatus.TUN_READY -> if (isFa) "سرویس VPN در حال اجراست" else "VPN service running"
                            TunnelStatus.BLOCKED_BY_KILL_SWITCH -> if (isFa) "مسدود — کیل‌سوئیچ" else "Blocked — kill switch"
                            TunnelStatus.FAILED -> if (isFa) "اتصال ناموفق" else "Connection failed"
                            else -> if (isFa) "متصل نیست" else "Not connected"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PanelText,
                    )
                }
                // Demo label: remove ONLY when TunnelStatus.CONNECTED is
                // reported by the native sing-box callback (not yet).
                if (!state.isConnected) {
                    Text(
                        if (isFa) "نمایشی" else "Demo",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentTeal,
                    )
                }
            }
            if (!state.isConnected) {
                Text(
                    if (isFa) "شبیه‌سازی، نه اتصال واقعی VPN" else "Simulation, not a real VPN connection",
                    style = MaterialTheme.typography.bodySmall,
                    color = PanelText.copy(alpha = 0.75f),
                )
            }
            if (state.status == TunnelStatus.TUN_READY) {
                Text(
                    if (isFa) "رابط TUN ساخته شد؛ هسته sing-box هنوز لینک نشده است." else "TUN built; sing-box core not linked yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PanelText.copy(alpha = 0.75f),
                )
            }
            if (state.isBlockedByKillSwitch) {
                Text(
                    if (isFa) "تونل قطع شد و کیل‌سوئیچ روشن است. مسدودسازی واقعی بسته‌ها تا لینک هسته، در انتظار است." else "Tunnel dropped with kill switch on. Real packet blocking is pending native linking.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PanelText.copy(alpha = 0.75f),
                )
            }
            state.activeConfig?.let { cfg ->
                Text(
                    "${cfg.countryNameFa} — ${cfg.host}:${cfg.port} · ${cfg.pingMs} ms",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PanelText,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (isFa) "کیل‌سوئیچ" else "Kill switch", color = PanelText)
                Switch(
                    checked = state.killSwitchOn,
                    onCheckedChange = onKillSwitchChange,
                    colors = SwitchDefaults.colors(checkedThumbColor = AccentTeal, checkedTrackColor = AccentTeal.copy(alpha = 0.4f)),
                )
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
    onConnect: (Config) -> Unit = {},
    onToggleStar: (Config) -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkBlue.copy(alpha = 0.65f)),
    ) {
        Column(modifier = Modifier.padding(14.dp, 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${group.flag} ${if (isFa) group.nameFa else group.nameEn}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PanelText,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isFa) "${group.healthyCount} کانفیگ سالم" else "${group.healthyCount} healthy configs",
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentTeal,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (expanded) "˄" else "˅", color = PanelText)
                }
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                group.configs.forEach { cfg ->
                    ConfigDetailRow(cfg = cfg, isFa = isFa, onToggleStar = onToggleStar)
                }
            }
        }
    }
}

/** One config row, exactly like the panel: badges, ping with bars, copy + star, no Connect button. */
@Composable
fun ConfigDetailRow(cfg: Config, isFa: Boolean, onToggleStar: (Config) -> Unit = {}) {
    val clipboard = LocalClipboardManager.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${cfg.countryNameFa} — ${cfg.host}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = PanelText)
            Text("${cfg.pingMs} ms", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (cfg.pingMs <= 150) AccentGreen else if (cfg.pingMs <= 300) Color(0xFFFFB020) else Color(0xFFFF6B6B))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(cfg.protocol.uppercase(), color = AccentTeal, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 8.dp))
            if (cfg.isReality) Text("REALITY", color = AccentTeal, style = MaterialTheme.typography.labelSmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { clipboard.setText(AnnotatedString("${cfg.protocol}://${cfg.host}:${cfg.port}")) }, shape = RoundedCornerShape(12.dp)) { Text(if (isFa) "کپی" else "Copy") }
            OutlinedButton(onClick = { onToggleStar(cfg) }, shape = RoundedCornerShape(12.dp)) { Text(if (cfg.isStarred) "★" else "☆") }
        }
    }
}

/** Support section at the bottom. No fabricated ID: empty means "not set yet". */
@Composable
fun SupportCard(supportId: String?, isFa: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp, 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkBlue.copy(alpha = 0.65f)),
    ) {
        Column(modifier = Modifier.padding(14.dp, 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (isFa) "پشتیبانی" else "Support", fontWeight = FontWeight.Bold, color = PanelText)
            Text(
                supportId ?: if (isFa) "هنوز تنظیم نشده است" else "Not set yet",
                style = MaterialTheme.typography.bodySmall,
                color = PanelText.copy(alpha = 0.75f),
            )
        }
    }
}
