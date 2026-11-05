package com.confighub.android.data

/**
 * Conceptual port of the Python collector (github-config-collector.py).
 * This class does NOT fetch GitHub in this skeleton and does NOT connect
 * a VPN. It encodes the same rules so the UI behaves like the collector:
 *  - country grouping is dynamic, built from the configs themselves
 *  - no country marker -> UNKNOWN, never guessed
 *  - healthy ceiling 380 ms, shared by counts, verified list and subscription
 *  - update interval 10 minutes, plus auto-update on open
 *  - incremental loading: ready configs are emitted as they arrive
 *
 * Real fetching (HTTP + TCP health check) belongs in a later step, using
 * the same sources and the same 380 ms rule, and must run off the UI thread.
 */
object Collector {
    const val HEALTHY_PING_MS = 380
    const val UPDATE_INTERVAL_MINUTES = 10L

    // Same 8 built-in GitHub sources as the Python collector.
    val SOURCES = listOf(
        "https://raw.githubusercontent.com/Alirewa/V2ray-Configs/main/config.txt",
        "https://raw.githubusercontent.com/mohammadaz2/v2rayConfigsForYou/main/configs.txt",
        "https://raw.githubusercontent.com/mohammadaz2/v2rayConfigsForYou/main/sub/vless.txt",
        "https://raw.githubusercontent.com/mohammadaz2/v2rayConfigsForYou/main/sub/vmess.txt",
        "https://raw.githubusercontent.com/mohammadaz2/v2rayConfigsForYou/main/sub/trojan.txt",
        "https://raw.githubusercontent.com/mohammadaz2/v2rayConfigsForYou/main/sub/ss.txt",
        "https://raw.githubusercontent.com/morpheusadam/v2ray-config/main/subs/bundles/best.txt",
        "https://raw.githubusercontent.com/morpheusadam/v2ray-config/main/subs/bundles/iran.txt",
    )

    private val FLAG_TO_CODE = mapOf(
        "🇩🇪" to "DE", "🇺🇸" to "US", "🇮🇷" to "IR", "🇳🇱" to "NL",
        "🇫🇷" to "FR", "🇬🇧" to "GB", "🇷🇺" to "RU", "🇹🇷" to "TR",
        "🇯🇵" to "JP", "🇰🇷" to "KR", "🇨🇦" to "CA", "🇮🇹" to "IT",
        "🇪🇸" to "ES", "🇸🇪" to "SE", "🇨🇭" to "CH", "🇦🇹" to "AT",
        "🇵🇱" to "PL", "🇧🇷" to "BR", "🇮🇳" to "IN", "🇦🇪" to "AE",
    )
    private val NAME_TO_CODE = mapOf(
        "germany" to "DE", "آلمان" to "DE", "usa" to "US", "america" to "US",
        "آمریکا" to "US", "iran" to "IR", "ایران" to "IR",
        "netherlands" to "NL", "هلند" to "NL", "france" to "FR",
        "فرانسه" to "FR", "united kingdom" to "GB", "بریتانیا" to "GB",
        "japan" to "JP", "ژاپن" to "JP", "canada" to "CA", "کانادا" to "CA",
        "russia" to "RU", "روسیه" to "RU", "turkey" to "TR", "ترکیه" to "TR",
    )
    private val CODE_TO_FA = mapOf(
        "DE" to "آلمان", "US" to "آمریکا", "IR" to "ایران", "NL" to "هلند",
        "FR" to "فرانسه", "GB" to "بریتانیا", "RU" to "روسیه", "TR" to "ترکیه",
        "JP" to "ژاپن", "KR" to "کره جنوبی", "CA" to "کانادا", "IT" to "ایتالیا",
        "ES" to "اسپانیا", "SE" to "سوئد", "CH" to "سوئیس", "AT" to "اتریش",
        "PL" to "لهستان", "BR" to "برزیل", "IN" to "هند", "AE" to "امارات",
    )
    private val CODE_TO_EN = mapOf(
        "DE" to "Germany", "US" to "USA", "IR" to "Iran", "NL" to "Netherlands",
        "FR" to "France", "GB" to "United Kingdom", "RU" to "Russia",
        "TR" to "Turkey", "JP" to "Japan", "KR" to "South Korea",
        "CA" to "Canada", "IT" to "Italy", "ES" to "Spain", "SE" to "Sweden",
        "CH" to "Switzerland", "AT" to "Austria", "PL" to "Poland",
        "BR" to "Brazil", "IN" to "India", "AE" to "UAE",
    )

    /** Country from a link remark only: flag, [XX], or a known name.
     *  Nothing is guessed: no marker means UNKNOWN. */
    fun countryFromRemark(remark: String): String {
        // Any flag emoji (two regional indicators) decodes to its code, e.g. 🇸🇬 -> SG.
        val flag = Regex("[\uD83C\uDDE6-\uD83C\uDDFF]{2}").find(remark)
        if (flag != null) {
            val s = flag.value
            val a = Character.codePointAt(s, 0) - 0x1F1E6
            val b = Character.codePointAt(s, 2) - 0x1F1E6
            if (a in 0..25 && b in 0..25) return "${('A' + a)}${('A' + b)}"
        }
        for ((flag, code) in FLAG_TO_CODE) if (remark.contains(flag)) return code
        val bracket = Regex("\\[([A-Za-z]{2})\\]").find(remark)
        if (bracket != null) return bracket.groupValues[1].uppercase()
        val lower = remark.lowercase()
        for ((name, code) in NAME_TO_CODE) if (lower.contains(name)) return code
        return "UNKNOWN"
    }

    /** Parse one config link into a Config with pingMs = -1 (unknown).
     *  Returns null for unsupported or malformed links. The caller must
     *  replace -1 with a real measured ping before showing it as healthy. */
    fun parseLink(line: String, index: Int): Config? {
        val text = line.trim()
        if (!text.contains("://")) return null
        val scheme = text.substringBefore("://").lowercase()
        val rest = text.substringAfter("://")
        val remarkRaw = rest.substringAfter("#", "")
        val remark = try {
            java.net.URLDecoder.decode(remarkRaw, "UTF-8")
        } catch (_: Exception) { remarkRaw }
        return try {
            when (scheme) {
                "vmess" -> {
                    val jsonText = String(
                        android.util.Base64.decode(
                            rest.substringBefore("#").trim(),
                            android.util.Base64.DEFAULT,
                        ), Charsets.UTF_8,
                    )
                    val host = Regex("\"add\"\\s*:\\s*\"([^\"]+)\"").find(jsonText)?.groupValues?.get(1) ?: return null
                    val port = Regex("\"port\"\\s*:\\s*\"?([0-9]+)\"?").find(jsonText)?.groupValues?.get(1)?.toIntOrNull() ?: return null
                    val code = countryFromRemark(remark)
                    Config(index.toString(), "vmess", host, port, code, CODE_TO_FA[code] ?: code, CODE_TO_EN[code] ?: code, -1)
                }
                "vless", "trojan", "ss", "hy2", "hysteria2", "tuic" -> {
                    val afterAt = rest.substringAfter("@", "")
                    if (afterAt.isEmpty()) return null
                    val hostPort = afterAt.substringBefore("?").substringBefore("/")
                    if (!hostPort.contains(":")) return null
                    val host = hostPort.substringBeforeLast(":")
                    val port = hostPort.substringAfterLast(":").toIntOrNull() ?: return null
                    val proto = when (scheme) { "hy2" -> "hysteria2"; else -> scheme }
                    val code = countryFromRemark(remark)
                    Config(index.toString(), proto, host, port, code, CODE_TO_FA[code] ?: code, CODE_TO_EN[code] ?: code, -1, isReality = text.lowercase().contains("reality"))
                }
                else -> null
            }
        } catch (_: Exception) { null }
    }

    /** Real ping: TCP connect time in ms, or -1 when it does not answer. */
    fun tcpPingMs(host: String, port: Int, timeoutMs: Int = 3000): Int {
        return try {
            val start = System.currentTimeMillis()
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(host, port), timeoutMs)
            }
            (System.currentTimeMillis() - start).toInt()
        } catch (_: Exception) { -1 }
    }

    /**
     * Update real configs from the GitHub sources, off the UI thread.
     * Each candidate gets a REAL TCP ping measured on this phone right
     * now; only candidates that answer within 380 ms and have a known
     * country are kept, at most 6 per country and 100 in total, sorted
     * by ping. Calls onResult on the main thread, onError on failure.
     * This does NOT connect a VPN: real connection stays future work.
     */
    fun updateFromSources(onResult: (List<Config>) -> Unit, onError: (String) -> Unit) {
        Thread {
            try {
                val candidates = linkedMapOf<String, Config>()
                var index = 1
                for (url in SOURCES) {
                    try {
                        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 10000
                        conn.readTimeout = 20000
                        conn.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                val cfg = parseLink(line, index)
                                if (cfg != null && cfg.countryCode != "UNKNOWN") {
                                    candidates["${cfg.protocol}://${cfg.host}:${cfg.port}"] = cfg
                                    index++
                                }
                            }
                        }
                    } catch (_: Exception) { /* one dead source must not kill the update */ }
                }
                val pool = java.util.concurrent.Executors.newFixedThreadPool(50)
                val checked = java.util.Collections.synchronizedList(mutableListOf<Config>())
                val latch = java.util.concurrent.CountDownLatch(candidates.size)
                for (cfg in candidates.values) {
                    pool.execute {
                        try {
                            val ms = tcpPingMs(cfg.host, cfg.port)
                            if (ms in 1..HEALTHY_PING_MS) checked.add(cfg.copy(pingMs = ms))
                        } finally { latch.countDown() }
                    }
                }
                latch.await(120, java.util.concurrent.TimeUnit.SECONDS)
                pool.shutdownNow()
                val kept = checked.sortedBy { it.pingMs }
                    .groupBy { it.countryCode }
                    .flatMap { (_, list) -> list.take(6) }
                    .take(100)
                    .mapIndexed { i, cfg -> cfg.copy(id = (i + 1).toString()) }
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (kept.isEmpty()) onError("no_healthy") else onResult(kept)
                }
            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onError(e.message ?: "update_failed")
                }
            }
        }.start()
    }

    /** Sort for a profile. Iran is NOT special-cased here: fastest means
     *  lowest ping for every country, including Iran (a bug found in the
     *  simulator was exactly that Iran was security-sorted even in fastest). */
    fun sortForProfile(configs: List<Config>, profile: Profile): List<Config> {
        val healthy = configs.filter { it.isHealthy }
        return when (profile) {
            Profile.FASTEST -> healthy.sortedBy { it.pingMs }
            Profile.SAFEST -> healthy.sortedWith(
                compareBy({ it.securityRank }, { it.pingMs })
            )
            Profile.FAVORITES -> healthy.filter { it.isStarred }.sortedBy { it.pingMs }
            Profile.RANDOM -> healthy.shuffled()
        }
    }

    /** Verified & fast list: top 6 healthy configs, following the profile. */
    fun verifiedFast(all: List<Config>, profile: Profile): List<Config> =
        sortForProfile(all, profile).take(6)

    /** Group configs by country code, dynamically. Unknown stays unknown. */
    fun groupByCountry(configs: List<Config>): List<CountryGroup> =
        configs.groupBy { it.countryCode }.map { (code, list) ->
            val first = list.first()
            CountryGroup(
                code = code,
                nameFa = first.countryNameFa,
                nameEn = first.countryNameEn,
                flag = flagFor(code),
                configs = list,
            )
        }.sortedBy { it.nameEn }

    private fun flagFor(code: String): String = when (code) {
        "US" -> "🇺🇸"; "DE" -> "🇩🇪"; "FR" -> "🇫🇷"; "NL" -> "🇳🇱"
        "GB" -> "🇬🇧"; "TR" -> "🇹🇷"; "AE" -> "🇦🇪"; "JP" -> "🇯🇵"
        "ID" -> "🇮🇩"; "CA" -> "🇨🇦"; "SE" -> "🇸🇪"; "IR" -> "🇮🇷"
        else -> "🏳"
    }

    /**
     * Incremental loading contract: the caller emits configs as they pass
     * the health check, instead of waiting for the whole run. The UI must
     * show a progress bar and let the user pick an early healthy config
     * while the rest are still arriving. This is a contract, not a fetch:
     * see README "what is NOT yet done".
     */
    interface IncrementalSink {
        fun onConfigReady(config: Config)
        fun onProgress(arrived: Int, total: Int)
        fun onDone()
    }
}
