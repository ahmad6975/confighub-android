package com.confighub.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.confighub.android.data.*
import com.confighub.android.ui.components.*
import com.confighub.android.ui.theme.AccentTeal
import com.confighub.android.ui.theme.DarkBlue
import com.confighub.android.ui.theme.PanelText
import com.confighub.android.vpn.TunnelManager

/**
 * Home screen layout, in the panel's approved order, kept tidy:
 *  1. tiny FA/EN toggle at the very top
 *  2. compact connection card
 *  3. separate, closed "add config" card (link only, never a country)
 *  4. compact profile bar (fastest / safest / favorites / random)
 *  5. closed verified & fast list
 *  6. country groups, collapsed by default
 *  7. support at the bottom
 * Extra tools (search, kill switch, failover, subscription formats) belong
 * behind "More" / admin, not on this main page.
 */
@Composable
fun HomeScreen(tunnelManager: TunnelManager) {
    var isFa by remember { mutableStateOf(true) }
    var profile by remember { mutableStateOf(Profile.FASTEST) }
    var expandedCountries by remember { mutableStateOf(setOf<String>()) }
    var verifiedExpanded by remember { mutableStateOf(false) }
    var addExpanded by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }
    val connection by tunnelManager.state.collectAsState()
    val context = LocalContext.current

    // Real configs, collected 2026-11-05 from the GitHub sources by the
    // Python collector (TCP-healthy, known country; ping is the ping
    // shown in the source remark). They are real server hosts, not
    // demo example domains — but the app still has NO real tunnel yet,
    // so tapping Connect stays a stub and real connection is future work.
    var configs by remember {
        mutableStateOf(
        listOf(
    Config("1", "vless", "dl1.6at.ir", 2157, "DE", "آلمان", "Germany", 128, isReality = true),
    Config("2", "vless", "de1.apexvpn.space", 8443, "DE", "آلمان", "Germany", 128, isReality = true),
    Config("3", "vless", "82.118.19.153", 443, "DE", "آلمان", "Germany", 3, isReality = true),
    Config("4", "hysteria2", "oh.sbbz.tech", 443, "US", "آمریکا", "USA", 128, isReality = false),
    Config("5", "hysteria2", "172.245.6.155", 18687, "US", "آمریکا", "USA", 128, isReality = false),
    Config("6", "hysteria2", "13.143.189.96", 4444, "US", "آمریکا", "USA", 128, isReality = false),
    Config("7", "vless", "176.119.141.136", 8443, "NL", "هلند", "Netherlands", 99, isReality = true),
    Config("8", "vless", "141.227.131.75", 443, "NL", "هلند", "Netherlands", 41, isReality = true),
    Config("9", "vless", "152.228.139.33", 35108, "FR", "فرانسه", "France", 5, isReality = true),
    Config("10", "vless", "18.183.215.124", 28573, "JP", "ژاپن", "Japan", 7, isReality = true),
    Config("11", "vless", "212.69.135.69", 8443, "CA", "کانادا", "Canada", 9, isReality = true),
    Config("12", "vless", "212.69.135.26", 8443, "CA", "کانادا", "Canada", 7, isReality = true),
)
        )
    }
    var added by remember { mutableStateOf<List<Config>>(emptyList()) }
    var updating by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf("") }
    var addStatus by remember { mutableStateOf("") }
    val groups = remember(configs) { Collector.groupByCountry(configs) }
    val verified = remember(configs, profile) { Collector.verifiedFast(configs, profile) }

    LazyColumn(modifier = Modifier.fillMaxSize().background(Color(0xFF0F2A33)), contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            // Very small language toggle at the very top.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { isFa = !isFa }) {
                    Text(if (isFa) "EN" else "FA")
                }
            }
            // Panel header, exactly like the simulator panel.
            Card(modifier = Modifier.fillMaxWidth().padding(12.dp, 8.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = DarkBlue.copy(alpha = 0.65f))) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp, 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("ConfigHub", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = PanelText)
                    Text("🌐", style = MaterialTheme.typography.titleLarge)
                }
            }
            var isAdmin by remember { mutableStateOf(false) }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (isFa) "نمایش:" else "View:", color = PanelText)
                Button(onClick = { isAdmin = false }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = if (!isAdmin) AccentTeal else DarkBlue, contentColor = if (!isAdmin) Color(0xFF0F2A33) else PanelText)) { Text(if (isFa) "عمومی" else "Public", maxLines = 1) }
                Button(onClick = { isAdmin = true }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isAdmin) AccentTeal else DarkBlue, contentColor = if (isAdmin) Color(0xFF0F2A33) else PanelText)) { Text(if (isFa) "ادمین" else "Admin", maxLines = 1) }
            }
            ConnectionCard(
                state = connection, isFa = isFa,
                onConnectBest = {
                    Collector.verifiedFast(configs, profile).firstOrNull()
                        ?.let { tunnelManager.connectBest(context, it, it.countryCode) }
                },
                onDisconnect = { tunnelManager.disconnect(context) },
                onKillSwitchChange = { tunnelManager.setKillSwitch(it) },
            )
            // Real update: fetch GitHub sources, measure a REAL TCP
            // ping on this phone right now, keep only healthy known-country
            // configs. This does NOT connect a VPN.
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp, 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        if (!updating) {
                            updating = true
                            updateStatus = if (isFa) "در حال بروزرسانی…" else "Updating…"
                            Collector.updateFromSources(
                                onResult = { fresh ->
                                    val freshKeys = fresh.map { "${it.host}:${it.port}" }.toSet()
                                    configs = fresh + added.filter { "${it.host}:${it.port}" !in freshKeys }
                                    updating = false
                                    updateStatus = if (isFa) "${fresh.size} کانفیگ واقعی تازه شد" else "${fresh.size} real configs updated"
                                },
                                onError = {
                                    updating = false
                                    updateStatus = if (isFa) "بروزرسانی ناموفق شد؛ دوباره امتحان کن" else "Update failed; try again"
                                },
                            )
                        }
                    },
                    enabled = !updating,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentTeal, contentColor = Color(0xFF0F2A33)),
                ) {
                    Text(if (isFa) "بروزرسانی" else "Update", fontWeight = FontWeight.Bold)
                }
                if (updateStatus.isNotEmpty()) {
                    Text(updateStatus, style = MaterialTheme.typography.bodySmall, color = PanelText.copy(alpha = 0.75f))
                }
            }
            // Add config: separate, closed, link-only. Country is
            // auto-detected in the real collector, never typed here.
            Card(modifier = Modifier.fillMaxWidth().padding(12.dp, 8.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = DarkBlue.copy(alpha = 0.65f))) {
                Column(modifier = Modifier.padding(14.dp, 12.dp)) {
                    TextButton(onClick = { addExpanded = !addExpanded }) {
                        Text(if (isFa) "+ افزودن کانفیگ" else "+ Add config")
                    }
                    if (addExpanded) {
                        OutlinedTextField(
                            value = link, onValueChange = { link = it },
                            label = { Text("vless://…") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            if (isFa) "فقط لینک کانفیگ؛ کشور خودکار تشخیص داده می‌شود."
                            else "Link only; country is auto-detected."
                        )
                        Button(
                            onClick = {
                                val parsed = Collector.parseLink(link, configs.size + 1)
                                if (parsed == null) {
                                    addStatus = if (isFa) "لینک نامعتبر است" else "Invalid link"
                                } else {
                                    addStatus = if (isFa) "در حال تست سرور…" else "Testing server…"
                                    Thread {
                                        val ms = Collector.tcpPingMs(parsed.host, parsed.port)
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            if (ms in 1..Collector.HEALTHY_PING_MS) {
                                                val addedCfg = parsed.copy(pingMs = ms, id = (configs.size + 1).toString())
                                                configs = configs + addedCfg
                                                added = added + addedCfg
                                                link = ""
                                                addStatus = if (isFa) "کانفیگ واقعی اضافه شد · ${ms} ms" else "Real config added · ${ms} ms"
                                            } else {
                                                addStatus = if (isFa) "سرور جواب نداد" else "Server did not respond"
                                            }
                                        }
                                    }.start()
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentTeal, contentColor = Color(0xFF0F2A33)),
                        ) {
                            Text(if (isFa) "افزودن" else "Add")
                        }
                        if (addStatus.isNotEmpty()) {
                            Text(addStatus, style = MaterialTheme.typography.bodySmall, color = PanelText.copy(alpha = 0.75f))
                        }
                    }
                }
            }
            // Compact profile bar.
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(listOf(Profile.FASTEST, Profile.SAFEST), listOf(Profile.FAVORITES, Profile.RANDOM)).forEach { rowProfiles ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        rowProfiles.forEach { p ->
                            val label = when (p) {
                                Profile.FASTEST -> if (isFa) "سریع‌ترین" else "Fastest"
                                Profile.SAFEST -> if (isFa) "امن‌ترین · استتار" else "Safest"
                                Profile.FAVORITES -> if (isFa) "علاقه‌مندی‌ها" else "Favorites"
                                Profile.RANDOM -> if (isFa) "تصادفی" else "Random"
                            }
                            FilterChip(
                                selected = profile == p,
                                onClick = { profile = p },
                                label = { Text(label, maxLines = 1) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentTeal, selectedLabelColor = Color(0xFF0F2A33)),
                            )
                        }
                    }
                }
            }
            // Verified & fast, closed by default.
            Card(modifier = Modifier.fillMaxWidth().padding(12.dp, 8.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = DarkBlue.copy(alpha = 0.65f))) {
                Column(modifier = Modifier.padding(14.dp, 12.dp)) {
                    TextButton(onClick = { verifiedExpanded = !verifiedExpanded }) {
                        Text(if (isFa) "تأییدشده و سریع" else "Verified & fast")
                    }
                    if (verifiedExpanded) {
                        verified.forEach { cfg ->
                            ConfigDetailRow(cfg = cfg, isFa = isFa, onToggleStar = { starred ->
                                configs = configs.map { if (it.id == starred.id) it.copy(isStarred = !it.isStarred) else it }
                            })
                        }
                    }
                }
            }
        }
        items(groups) { group ->
            CountryGroupCard(
                group = group, isFa = isFa,
                expanded = group.code in expandedCountries,
                onToggle = {
                    expandedCountries = if (group.code in expandedCountries)
                        expandedCountries - group.code else expandedCountries + group.code
                },
                onToggleStar = { starred ->
                    configs = configs.map { if (it.id == starred.id) it.copy(isStarred = !it.isStarred) else it }
                },
            )
        }
        item {
            // No fabricated ID: null means "not set yet" (admin sets it later).
            SupportCard(supportId = null, isFa = isFa)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
