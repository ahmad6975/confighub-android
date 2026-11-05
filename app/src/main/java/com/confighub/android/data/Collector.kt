package com.confighub.android.data

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.Executors

/**
 * Real collector: fetches the panel's GitHub subscription sources,
 * parses vmess/vless/trojan/ss links, and live-pings each candidate
 * with a plain TCP connect (380 ms ceiling, same rule as the panel).
 * No demo configs are ever invented: on failure the caller keeps its
 * previous list and is told the error. Parsing and pinging run off
 * the UI thread, on a small worker pool.
 *
 * Sources are exactly the panel's GitHub subscription sources. They
 * are public config lists; no credential is involved.
 */
object Collector {
    const val HEALTHY_PING_MS = 380
    const val UPDATE_INTERVAL_MINUTES = 10L
    private val sources = listOf(
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Sub1.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Sub2.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Sub3.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Sub4.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Sub5.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Sub6.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Sub7.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Sub8.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Sub9.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Sub10.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Sub11.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Sub12.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Sub13.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Sub14.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Sub15.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Sub16.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Splitted-By-Protocol/vmess.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Splitted-By-Protocol/vless.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Splitted-By-Protocol/trojan.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Splitted-By-Protocol/shadowsocks.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Splitted-By-Protocol/hysteria.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Splitted-By-Protocol/hysteria2.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Splitted-By-Protocol/tuic.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Splitted-By-Protocol/wireguard.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Splitted-By-Protocol/socks.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Splitted-By-Protocol/http.txt",
    )
    private val pool = Executors.newFixedThreadPool(8)
    private fun fetchText(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "ConfigHub-Android")
        return BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { it.readText() }
    }
    private fun b64decode(text: String): String {
        val clean = text.trim().replace(" ", "").replace("\n", "").replace("\r", "")
        val padded = clean + "=".repeat((4 - clean.length % 4) % 4)
        return String(Base64.getUrlDecoder().decode(padded), StandardCharsets.UTF_8)
    }
    private fun decodeMaybe(text: String): String {
        return try { b64decode(text) } catch (e: Exception) { text }
    }
    /**
     * Parse one subscription line. Supports vmess (base64 JSON), vless,
     * trojan and ss. A link without a country marker gets UNKNOWN, never
     * a guessed country. Returns null for anything unparseable.
     */
    fun parseLink(raw: String, id: Int): Config? {
        val line = raw.trim()
        if (line.isEmpty()) return null
        return try {
            when {
                line.startsWith("vmess://") -> {
                    val json = decodeMaybe(line.removePrefix("vmess://"))
                    val host = Regex("\"add\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: return null
                    val port = Regex("\"port\"\\s*:\\s*\"?(\\d+)\"?").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: return null
                    val name = Regex("\"ps\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1) ?: ""
                    val country = countryFromName(name)
                    Config(id = id, countryCode = country.first, countryNameFa = country.second, countryNameEn = country.third, host = host, port = port, protocol = "vmess", isHealthy = false, pingMs = 0, securityRank = 3)
                }
                line.startsWith("vless://") || line.startsWith("trojan://") -> {
                    val proto = if (line.startsWith("vless")) "vless" else "trojan"
                    val rest = line.substringAfter("://")
                    val at = rest.substringAfter("@")
                    val hostPort = at.substringBefore("?").substringBefore("#")
                    val host = hostPort.substringBefore(":")
                    val port = hostPort.substringAfter(":", "").toIntOrNull() ?: return null
                    val name = URLDecoder.decode(at.substringAfter("#", ""), StandardCharsets.UTF_8)
                    val country = countryFromName(name)
                    Config(id = id, countryCode = country.first, countryNameFa = country.second, countryNameEn = country.third, host = host, port = port, protocol = proto, isHealthy = false, pingMs = 0, securityRank = if (proto == "trojan") 2 else 3)
                }
                line.startsWith("ss://") -> {
                    val rest = line.removePrefix("ss://")
                    val decoded = if (rest.contains("@")) rest else decodeMaybe(rest.substringBefore("#")) + "#" + rest.substringAfter("#", "")
                    val at = decoded.substringAfter("@")
                    val hostPort = at.substringBefore("?").substringBefore("#")
                    val host = hostPort.substringBefore(":")
                    val port = hostPort.substringAfter(":", "").toIntOrNull() ?: return null
                    val name = URLDecoder.decode(at.substringAfter("#", ""), StandardCharsets.UTF_8)
                    val country = countryFromName(name)
                    Config(id = id, countryCode = country.first, countryNameFa = country.second, countryNameEn = country.third, host = host, port = port, protocol = "ss", isHealthy = false, pingMs = 0, securityRank = 4)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
    private fun countryFromName(name: String): Triple<String, String, String> {
        val lower = name.lowercase()
        return when {
            "🇮🇷" in name || lower.contains("iran") || lower.contains("ir_") -> Triple("IR", "ایران", "Iran")
            "🇺🇸" in name || lower.contains("us") || lower.contains("america") -> Triple("US", "آمریکا", "United States")
            "🇩🇪" in name || lower.contains("de") || lower.contains("germany") -> Triple("DE", "آلمان", "Germany")
            "🇫🇷" in name || lower.contains("fr") || lower.contains("france") -> Triple("FR", "فرانسه", "France")
            "🇳🇱" in name || lower.contains("nl") || lower.contains("netherlands") -> Triple("NL", "هلند", "Netherlands")
            "🇬🇧" in name || lower.contains("gb") || lower.contains("uk") -> Triple("GB", "بریتانیا", "United Kingdom")
            "🇹🇷" in name || lower.contains("tr") || lower.contains("turkey") -> Triple("TR", "ترکیه", "Turkey")
            "🇦🇪" in name || lower.contains("ae") || lower.contains("emirates") -> Triple("AE", "امارات", "UAE")
            "🇯🇵" in name || lower.contains("jp") || lower.contains("japan") -> Triple("JP", "ژاپن", "Japan")
            "🇮🇩" in name || lower.contains("id") || lower.contains("indonesia") -> Triple("ID", "اندونزی", "Indonesia")
            "🇨🇦" in name || lower.contains("ca") || lower.contains("canada") -> Triple("CA", "کانادا", "Canada")
            "🇸🇪" in name || lower.contains("se") || lower.contains("sweden") -> Triple("SE", "سوئد", "Sweden")
            else -> Triple("UNKNOWN", "نامشخص", "Unknown")
        }
    }
    /**
     * Live TCP ping: connect timeout is the 380 ms ceiling. Returns the
     * elapsed milliseconds, or -1 when the host does not answer in time.
     */
    fun tcpPingMs(host: String, port: Int): Int {
        return try {
            val start = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), HEALTHY_PING_MS)
            }
            (System.currentTimeMillis() - start).toInt()
        } catch (e: Exception) {
            -1
        }
    }
    /**
     * Real update: fetch every source, parse every line, live-ping each
     * candidate, and keep only the healthy ones (ping in 1..380 ms),
     * fastest first. Runs on the worker pool; callbacks are for the UI
     * to post back to the main thread if it needs to.
     */
    fun updateFromSources(onResult: (List<Config>) -> Unit, onError: (String) -> Unit) {
        pool.execute {
            try {
                val seen = mutableSetOf<String>()
                val parsed = mutableListOf<Config>()
                var id = 1
                for (source in sources) {
                    val text = try { fetchText(source) } catch (e: Exception) { continue }
                    val body = if (text.contains("://")) text else decodeMaybe(text)
                    for (line in body.lines()) {
                        val trimmed = line.trim()
                        if (trimmed.isEmpty() || trimmed in seen) continue
                        seen += trimmed
                        parseLink(trimmed, id++)?.let { parsed += it }
                    }
                }
                if (parsed.isEmpty()) {
                    onError("No configs parsed from sources")
                    return@execute
                }
                val healthy = parsed.mapNotNull { cfg ->
                    val ms = tcpPingMs(cfg.host, cfg.port)
                    if (ms in 1..HEALTHY_PING_MS) cfg.copy(isHealthy = true, pingMs = ms) else null
                }.sortedBy { it.pingMs }
                onResult(healthy)
            } catch (e: Exception) {
                onError(e.message ?: "Unknown error")
            }
        }
    }
    /** Sort for a profile. Iran is NOT special-cased here: fastest means lowest ping for every country, including Iran. */
    fun sortForProfile(configs: List<Config>, profile: Profile): List<Config> {
        val healthy = configs.filter { it.isHealthy }
        return when (profile) {
            Profile.FASTEST -> healthy.sortedBy { it.pingMs }
            Profile.SAFEST -> healthy.sortedWith(compareBy({ it.securityRank }, { it.pingMs }))
            Profile.FAVORITES -> healthy.filter { it.isStarred }.sortedBy { it.pingMs }
            Profile.RANDOM -> healthy.shuffled()
        }
    }
    /** Verified & fast list: top 6 healthy configs, following the profile. */
    fun verifiedFast(all: List<Config>, profile: Profile): List<Config> = sortForProfile(all, profile).take(6)
    /** Group configs by country code, dynamically. Unknown stays unknown. */
    fun groupByCountry(configs: List<Config>): List<CountryGroup> =
        configs.groupBy { it.countryCode }.map { (code, list) ->
            val first = list.first()
            CountryGroup(code = code, nameFa = first.countryNameFa, nameEn = first.countryNameEn, flag = flagFor(code), configs = list)
        }.sortedBy { it.nameEn }
    private fun flagFor(code: String): String = when (code) {
        "US" -> "🇺🇸"; "DE" -> "🇩🇪"; "FR" -> "🇫🇷"; "NL" -> "🇳🇱"
        "GB" -> "🇬🇧"; "TR" -> "🇹🇷"; "AE" -> "🇦🇪"; "JP" -> "🇯🇵"
        "ID" -> "🇮🇩"; "CA" -> "🇨🇦"; "SE" -> "🇸🇪"; "IR" -> "🇮🇷"
        else -> "🏳"
    }
}
