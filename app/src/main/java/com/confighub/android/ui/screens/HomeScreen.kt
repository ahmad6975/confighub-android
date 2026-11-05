package com.confighub.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.confighub.android.data.*
import com.confighub.android.ui.components.*
import com.confighub.android.ui.theme.AccentTeal
import com.confighub.android.ui.theme.DarkBlue
import com.confighub.android.ui.theme.PanelText
import com.confighub.android.vpn.TunnelManager

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
    var updateStatus by remember { mutableStateOf("") }
    var configs by remember {
        mutableStateOf(
            listOf(
                Config("1", "vmess", "dl1.6at.ir", 443, "IR", "ایران", "Iran", 320, isReality = true, isObfuscated = false, isStarred = true),
                Config("2", "vless", "dl2.6at.ir", 443, "IR", "ایران", "Iran", 340, isReality = true, isObfuscated = false, isStarred = false),
                Config("3", "vless", "us1.example.com", 443, "US", "آمریکا", "USA", 180, isReality = true, isObfuscated = false, isStarred = false),
                Config("4", "trojan", "de1.example.com", 443, "DE", "آلمان", "Germany", 210, isReality = false, isObfuscated = false, isStarred = false),
                Config("5", "ss", "nl1.example.com", 8388, "NL", "هلند", "Netherlands", 240, isReality = false, isObfuscated = true, isStarred = false),
                Config("6", "vless", "fr1.example.com", 443, "FR", "فرانسه", "France", 260, isReality = false, isObfuscated = false, isStarred = false),
                Config("7", "vmess", "gb1.example.com", 443, "GB", "بریتانیا", "United Kingdom", 230, isReality = true, isObfuscated = false, isStarred = false),
                Config("8", "trojan", "tr1.example.com", 443, "TR", "ترکیه", "Turkey", 290, isReality = false, isObfuscated = false, isStarred = false),
                Config("9", "vless", "jp1.example.com", 443, "JP", "ژاپن", "Japan", 310, isReality = true, isObfuscated = false, isStarred = false),
                Config("10", "ss", "ca1.example.com", 8388, "CA", "کانادا", "Canada", 270, isReality = false, isObfuscated = true, isStarred = false),
                Config("11", "vmess", "se1.example.com", 443, "SE", "سوئد", "Sweden", 250, isReality = false, isObfuscated = false, isStarred = false),
                Config("12", "vless", "ae1.example.com", 443, "AE", "امارات", "UAE", 300, isReality = false, isObfuscated = false, isStarred = false),
            )
        )
    }
    var added by remember { mutableStateOf<List<Config>>(emptyList()) }
    var lastUpdate by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val connection by tunnelManager.state.collectAsState()
    val context = LocalContext.current
    val all = configs + added
    val sorted = Collector.sortForProfile(all, profile)
    val groups = Collector.groupByCountry(sorted)
    val verified = Collector.verifiedFast(all, profile)

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0F2A33)),
        contentPadding = PaddingValues(12.dp, 8.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp, 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("ConfigHub", color = PanelText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { isFa = !isFa }) {
                    Text(if (isFa) "EN" else "فارسی", color = AccentTeal)
                }
            }
        }

        item {
            ConnectionCard(
                state = connection,
                isFa = isFa,
                onConnectBest = {
                    val best = verified.firstOrNull() ?: sorted.firstOrNull()
                    if (best != null) tunnelManager.connectBest(context, listOf(best))
                },
                onDisconnect = { tunnelManager.disconnect() },
                onKillSwitchChange = { tunnelManager.setKillSwitch(it) },
            )
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp, 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isFa) "بروز شده: ${lastUpdate.ifEmpty { "—" }}" else "Updated: ${lastUpdate.ifEmpty { "—" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = PanelText.copy(alpha = 0.75f),
                )
                Button(
                    onClick = {
                        if (!updating) {
                            updating = true
                            error = ""
                            Collector.updateFromSources(
                                onResult = { fresh ->
                                    configs = fresh
                                    lastUpdate = if (isFa) "بهروزرسانی شد" else "Updated"
                                    updating = false
                                },
                                onError = { msg ->
                                    error = msg
                                    updating = false
                                },
                            )
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentTeal, contentColor = Color(0xFF0F2A33)),
                ) {
                    Text(if (updating) (if (isFa) "در حال بهروزرسانی…" else "Updating…") else (if (isFa) "بهروزرسانی" else "Update"))
                }
            }
            if (updating) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            if (error.isNotEmpty()) Text(error, color = Color(0xFFFF6B6B), modifier = Modifier.padding(horizontal = 12.dp))
            if (updateStatus.isNotEmpty()) Text(updateStatus, style = MaterialTheme.typography.bodySmall, color = PanelText.copy(alpha = 0.75f), modifier = Modifier.padding(horizontal = 12.dp))
        }

        item {
            Card(modifier = Modifier.fillMaxWidth().padding(12.dp, 8.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = DarkBlue.copy(alpha = 0.65f))) {
                Column(modifier = Modifier.padding(14.dp, 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isFa) "افزودن کانفیگ" else "Add config", fontWeight = FontWeight.Bold, color = PanelText)
                        TextButton(onClick = { addExpanded = !addExpanded }) {
                            Text(if (addExpanded) "˄" else "˅", color = PanelText)
                        }
                    }
                    if (addExpanded) {
                        OutlinedTextField(
                            value = link,
                            onValueChange = { link = it },
                            label = { Text(if (isFa) "لینک کانفیگ" else "Config link") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                val cfg = Collector.parseLink(link, 1000 + added.size)
                                if (cfg == null || cfg.countryCode == "UNKNOWN") {
                                    linkMsg = if (isFa) "لینک نامعتبر است" else "Invalid link"
                                } else {
                                    added = added + cfg
                                    link = ""
                                    linkMsg = if (isFa) "اضافه شد" else "Added"
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentTeal, contentColor = Color(0xFF0F2A33)),
                        ) {
                            Text(if (isFa) "افزودن" else "Add")
                        }
                        if (linkMsg.isNotEmpty()) Text(linkMsg, color = PanelText)
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp, 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Profile.values().forEach { p ->
                    FilterChip(
                        selected = profile == p,
                        onClick = { profile = p },
                        label = {
                            Text(
                                when (p) {
                                    Profile.FASTEST -> if (isFa) "سریعترین" else "Fastest"
                                    Profile.SAFEST -> if (isFa) "امنترین" else "Safest"
                                    Profile.FAVORITES -> if (isFa) "علاقهمندیها" else "Favorites"
                                    Profile.RANDOM -> if (isFa) "تصادفی" else "Random"
                                }
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentTeal, selectedLabelColor = Color(0xFF0F2A33)),
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth().padding(12.dp, 8.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = DarkBlue.copy(alpha = 0.65f))) {
                Column(modifier = Modifier.padding(14.dp, 12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isFa) "تاییدشده و سریع" else "Verified & fast", fontWeight = FontWeight.Bold, color = PanelText)
                        TextButton(onClick = { verifiedExpanded = !verifiedExpanded }) {
                            Text(if (verifiedExpanded) "˄" else "˅", color = PanelText)
                        }
                    }
                    if (verifiedExpanded) {
                        verified.forEach { cfg ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("${cfg.protocol} · ${cfg.host}:${cfg.port} · ${cfg.pingMs} ms", style = MaterialTheme.typography.bodySmall, color = PanelText)
                                Button(
                                    onClick = { tunnelManager.connectBest(context, listOf(cfg)) },
                                    enabled = cfg.isHealthy,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentTeal, contentColor = Color(0xFF0F2A33)),
                                ) {
                                    Text(if (isFa) "اتصال" else "Connect")
                                }
                            }
                        }
                    }
                }
            }
        }

        items(groups, key = { it.code }) { group ->
            CountryGroupCard(
                group = group,
                isFa = isFa,
                expanded = expandedCountries.contains(group.code),
                onToggle = {
                    expandedCountries = if (expandedCountries.contains(group.code)) expandedCountries - group.code else expandedCountries + group.code
                },
                onConnect = { cfg -> tunnelManager.connectBest(context, listOf(cfg)) },
            )
        }

        item {
            SupportCard(supportId = null, isFa = isFa)
        }
    }
}
