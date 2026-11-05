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
import kotlinx.coroutines.launch

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
 *
 * Real configs only: on open, and on Update, the collector fetches the
 * GitHub sources and live-pings every candidate (TCP, 380 ms ceiling).
 * While that fetch is running the page shows a progress bar and keeps
 * the previous real configs; it never invents demo servers. Real VPN
 * connection stays future work: connect buttons are verified stubs and
 * the demo label stays visible while the tunnel is a stub.
 */
@Composable
fun HomeScreen(tunnelManager: TunnelManager) {
    var isFa by remember { mutableStateOf(true) }
    var profile by remember { mutableStateOf(Profile.FASTEST) }
    var expandedCountries by remember { mutableStateOf(setOf<String>()) }
    var verifiedExpanded by remember { mutableStateOf(false) }
    var addExpanded by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }
    var linkMsg by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var updating by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var configs by remember { mutableStateOf<List<Config>>(emptyList()) }
    var added by remember { mutableStateOf<List<Config>>(emptyList()) }
    var lastUpdate by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val connection by tunnelManager.state.collectAsState()
    val context = LocalContext.current
    // Real update: GitHub sources + live TCP ping. Keeps the previous
    // list on failure and reports the failure, never invents configs.
    fun refresh() {
        if (updating) return
        updating = true
        error = ""
        Collector.updateFromSources(
            onResult = { fresh ->
                configs = fresh
                progress = 0
                lastUpdate = if (isFa) "بهروزرسانی شد" else "Updated"
                updating = false
            },
            onError = { err ->
                error = if (isFa) "بهروزرسانی ناموفق: $err" else "Update failed: $err"
                updating = false
            },
        )
    }
    LaunchedEffect(Unit) { refresh() }
    val all = configs + added
    val groups = remember(all) { Collector.groupByCountry(all) }
    val verified = remember(all, profile) { Collector.verifiedFast(all, profile) }
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
                    Collector.verifiedFast(all, profile).firstOrNull()
                        ?.let { tunnelManager.connectBest(context, it, it.countryCode) }
                },
                onDisconnect = { tunnelManager.disconnect(context) },
                onKillSwitchChange = { tunnelManager.setKillSwitch(it) },
            )
            // Real update button + live ping status, directly under the
            // card so the user sees the update running, not a demo.
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp, 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = { refresh() }, enabled = !updating) {
                    Text(if (isFa) "بهروزرسانی" else "Update")
                }
                Text(if (updating) (if (isFa) "در حال بهروزرسانی و پینگ…" else "Updating and pinging…") else lastUpdate)
            }
            if (updating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(12.dp, 0.dp))
            }
            if (error.isNotEmpty()) {
                Text(error, modifier = Modifier.padding(12.dp))
            }
            if (configs.isEmpty() && !updating) {
                Text(if (isFa) "هنوز کانفیگی یافت نشد؛ «بهروزرسانی» را بزنید." else "No configs yet; press Update.", modifier = Modifier.padding(12.dp))
            }
            // Add-config card, closed by default, as in the panel.
            // A submitted link is parsed with the same parseLink rules,
            // live-pinged, and only then added; it never becomes a country.
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    TextButton(onClick = { addExpanded = !addExpanded }) {
                        Text(if (isFa) "افزودن کانفیگ" else "Add config")
                    }
                    if (addExpanded) {
                        OutlinedTextField(
                            value = link, onValueChange = { link = it },
                            label = { Text(if (isFa) "لینک کانفیگ" else "Config link") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = {
                            val cfg = Collector.parseLink(link, 1000 + added.size)
                            if (cfg == null) {
                                linkMsg = if (isFa) "لینک نامعتبر است" else "Invalid link"
                            } else {
                                scope.launch {
                                    val ms = Collector.tcpPingMs(cfg.host, cfg.port)
                                    if (ms in 1..Collector.HEALTHY_PING_MS) {
                                        added = added + cfg.copy(pingMs = ms)
                                        linkMsg = if (isFa) "کانفیگ اضافه شد (${ms} ms)" else "Config added (${ms} ms)"
                                    } else {
                                        linkMsg = if (isFa) "پینگ ناموفق بود" else "Ping failed"
                                    }
                                }
                            }
                        }, modifier = Modifier.padding(top = 8.dp)) {
                            Text(if (isFa) "ثبت" else "Submit")
                        }
                        if (linkMsg.isNotEmpty()) Text(linkMsg, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            // Compact profile bar.
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp, 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Profile.values().forEach { p ->
                    TextButton(onClick = { profile = p }) {
                        Text(when (p) {
                            Profile.FASTEST -> if (isFa) "سریعترین" else "Fastest"
                            Profile.SAFEST -> if (isFa) "امنترین" else "Safest"
                            Profile.FAVORITES -> if (isFa) "علاقهمندیها" else "Favorites"
                            Profile.RANDOM -> if (isFa) "تصادفی" else "Random"
                        })
                    }
                }
            }
            // Closed verified & fast list.
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Column(modifier = Modifier.padding(4.dp)) {
                    TextButton(onClick = { verifiedExpanded = !verifiedExpanded }) {
                        Text(if (isFa) "تأییدشده و سریع" else "Verified & fast")
                    }
                    if (verifiedExpanded) {
                        VerifiedList(verified, isFa) { cfg ->
                            tunnelManager.connectBest(context, cfg, cfg.countryCode)
                        }
                    }
                }
            }
        }
        // Country groups, collapsed by default.
        items(groups) { group ->
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp, 4.dp)) {
                Column {
                    CountryRow(group, isFa) {
                        expandedCountries = if (group.code in expandedCountries) {
                            expandedCountries - group.code
                        } else {
                            expandedCountries + group.code
                        }
                    }
                    if (group.code in expandedCountries) {
                        group.configs.forEach { cfg ->
                            ConfigRow(cfg, isFa) {
                                tunnelManager.connectBest(context, cfg, cfg.countryCode)
                            }
                        }
                    }
                }
            }
        }
        item {
            DemoNote(isFa)
            // Support at the bottom.
            Text(if (isFa) "پشتیبانی" else "Support", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(12.dp))
            Text("confighub", modifier = Modifier.padding(12.dp, 0.dp, 12.dp, 12.dp))
        }
    }
}
