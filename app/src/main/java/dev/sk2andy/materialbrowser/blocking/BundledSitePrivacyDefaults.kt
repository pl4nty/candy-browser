package dev.sk2andy.materialbrowser.blocking

import android.content.Context

internal class BundledSitePrivacyDefaults private constructor(
    private val forceVerticalScrollHosts: Set<String>,
    private val cookieBannerRemovalDisabledHosts: Set<String>,
) {
    fun forceVerticalScrolling(host: String?): Boolean =
        normalizedHost(host) in forceVerticalScrollHosts

    fun cookieBannerRemovalDisabled(host: String?): Boolean =
        normalizedHost(host) in cookieBannerRemovalDisabledHosts

    fun forceSafeArea(host: String?): Boolean =
        normalizedHost(host) in FORCE_SAFE_AREA_HOSTS

    private fun normalizedHost(host: String?): String? =
        PrivacyRequestSanitizer.normalizeHost(host)

    companion object {
        private const val ASSET_NAME = "site_privacy_defaults.txt"
        private const val COOKIE_BANNER_REMOVAL_DISABLED = "cookie_banner_removal_disabled"
        private const val FORCE_VERTICAL_SCROLL = "force_vertical_scroll"
        private const val FORMAT_HEADER = "# candy site privacy defaults v2"

        // Hosts that start below the status bar, as if "Force safe area" were on. Kept out of the
        // generated asset so that regenerating it from the audit keeps them.
        // Facebook reels lock scrolling and size the reel to the viewport, then scroll the page
        // while loading. Edge-to-edge CSS lets the reel slide under Facebook's top bar, which then
        // covers "Tap to unmute"; the layout differs by region, so CSS detection misses some.
        private val FORCE_SAFE_AREA_HOSTS = setOf("www.facebook.com", "m.facebook.com", "facebook.com")

        val Empty = BundledSitePrivacyDefaults(emptySet(), emptySet())

        fun load(context: Context): BundledSitePrivacyDefaults = runCatching {
            context.applicationContext.assets.open(ASSET_NAME).bufferedReader().use { reader ->
                parse(reader.readText())
            }
        }.getOrDefault(Empty)

        fun parse(text: String): BundledSitePrivacyDefaults {
            val lines = text.lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toList()
            require(lines.firstOrNull() == FORMAT_HEADER) {
                "Missing bundled site privacy defaults header"
            }
            val forceVerticalScrollHosts = linkedSetOf<String>()
            val cookieBannerRemovalDisabledHosts = linkedSetOf<String>()
            val assignedHosts = linkedSetOf<String>()
            lines.asSequence()
                .filterNot { line -> line.startsWith('#') }
                .forEach { line ->
                    val fields = line.split('\t')
                    require(fields.size == 2) { "Invalid bundled site privacy default: $line" }
                    val (rule, host) = fields
                    require(PrivacyRequestSanitizer.normalizeHost(host) == host) {
                        "Invalid bundled site privacy host: $host"
                    }
                    require(assignedHosts.add(host)) { "Duplicate bundled site privacy host: $host" }
                    when (rule) {
                        COOKIE_BANNER_REMOVAL_DISABLED ->
                            cookieBannerRemovalDisabledHosts += host
                        FORCE_VERTICAL_SCROLL -> forceVerticalScrollHosts += host
                        else -> error("Invalid bundled site privacy default: $line")
                    }
                }
            return BundledSitePrivacyDefaults(
                forceVerticalScrollHosts = forceVerticalScrollHosts,
                cookieBannerRemovalDisabledHosts = cookieBannerRemovalDisabledHosts,
            )
        }
    }
}
