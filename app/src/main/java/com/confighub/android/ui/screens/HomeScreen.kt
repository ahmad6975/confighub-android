package com.confighub.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.confighub.android.data.*
import com.confighub.android.ui.components.*
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

    // Demo seed only for the skeleton UI. Real configs come from the
    // collector in a later step; nothing here is a real working server.
    val demoConfigs = remember {
        listOf(
            Config("1", "vless", "example-de.invalid", 443, "DE", "آلمان", "Germany", 120, isReality = true),
            Config("2", "vless", "example-us.invalid", 443, "US", "آمریکا", "USA", 190),
            Config("3", "trojan", "example-ir.invalid", 443, "IR", "ایران", "Iran", 90, isObfuscated = true, isStarred = true),
        )
    }
    val groups = remember(demoConfigs) { Collector.groupByCountry(demoConfigs) }
    val verified = remember(demoConfigs, profile) { Collector.verifiedFast(demoConfigs, profile) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            // Very small language toggle at the very top.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { isFa = !isFa }) {
                    Text(if (isFa) "EN" else "FA")
                }
            }
            ConnectionCard(
                state = connection, isFa = isFa,
                onConnectBest = {
                    Collector.verifiedFast(demoConfigs, profile).firstOrNull()
                        ?.let { tunnelManager.connectBest(context, it, it.countryCode) }
                },
                onDisconnect = { tunnelManager.disconnect(context) },
                onKillSwitchChange = { tunnelManager.setKillSwitch(it) },
            )
            // Add config: separate, closed, link-only. Country is
            // auto-detected in the real collector, never typed here.
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Column(modifier = Modifier.padding(12.dp, 8.dp)) {
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
                    }
                }
            }
            // Compact profile bar.
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Profile.values().forEach { p ->
                    val label = when (p) {
                        Profile.FASTEST -> if (isFa) "سریع‌ترین" else "Fastest"
                        Profile.SAFEST -> if (isFa) "امن‌ترین · استتار" else "Safest"
                        Profile.FAVORITES -> if (isFa) "علاقه‌مندی‌ها" else "Favorites"
                        Profile.RANDOM -> if (isFa) "تصادفی" else "Random"
                    }
                    FilterChip(
                        selected = profile == p,
                        onClick = { profile = p },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
            }
            // Verified & fast, closed by default.
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Column(modifier = Modifier.padding(12.dp, 8.dp)) {
                    TextButton(onClick = { verifiedExpanded = !verifiedExpanded }) {
                        Text(if (isFa) "تأییدشده و سریع" else "Verified & fast")
                    }
                    if (verifiedExpanded) {
                        verified.forEach { cfg ->
                            Text("${cfg.protocol} · ${cfg.host}:${cfg.port} · ${cfg.pingMs} ms")
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
                onConnect = { tunnelManager.connect(context, it) },
            )
        }
        item {
            // No fabricated ID: null means "not set yet" (admin sets it later).
            SupportCard(supportId = null, isFa = isFa)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
