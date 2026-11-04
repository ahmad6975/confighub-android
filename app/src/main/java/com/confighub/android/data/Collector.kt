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
