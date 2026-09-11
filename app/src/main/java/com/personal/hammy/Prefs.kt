package com.personal.hammy

import android.content.Context

object Prefs {
    private const val NAME = "hammy_prefs"
    private const val KEY_AGE_OK = "age_confirmed"
    private const val KEY_ORIENTATION = "orientation"
    private const val KEY_CATEGORIES = "categories"
    private const val KEY_SETUP_DONE = "setup_done"

    enum class Orientation(val key: String, val homeUrl: String, val categoryPrefix: String) {
        STRAIGHT("straight", "https://xhamster.com/", "https://xhamster.com/categories/"),
        GAY("gay", "https://xhamster.com/gay", "https://xhamster.com/gay/categories/"),
        TRANS("trans", "https://xhamster.com/shemale", "https://xhamster.com/shemale/categories/");

        /**
         * Combined AND search URL for selected category slugs.
         * Straight: /search/slug1+slug2
         * Gay: /gay/search/...
         * Trans: /shemale/search/...
         */
        fun combinedSearchUrl(slugs: Collection<String>): String? {
            if (slugs.isEmpty()) return null
            val path = slugs.joinToString("+") { slug ->
                slug.trim().replace(" ", "%20")
            }
            return when (this) {
                STRAIGHT -> "https://xhamster.com/search/$path"
                GAY -> "https://xhamster.com/gay/search/$path"
                TRANS -> "https://xhamster.com/shemale/search/$path"
            }
        }

        companion object {
            fun fromKey(key: String?): Orientation =
                entries.firstOrNull { it.key == key } ?: STRAIGHT
        }
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isAgeConfirmed(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_AGE_OK, false)

    fun setAgeConfirmed(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_AGE_OK, value).apply()
    }

    fun isSetupDone(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_SETUP_DONE, false)

    fun setSetupDone(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_SETUP_DONE, value).apply()
    }

    fun getOrientation(ctx: Context): Orientation =
        Orientation.fromKey(prefs(ctx).getString(KEY_ORIENTATION, null))

    fun setOrientation(ctx: Context, orientation: Orientation) {
        prefs(ctx).edit().putString(KEY_ORIENTATION, orientation.key).apply()
    }

    fun getSelectedSlugs(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY_CATEGORIES, emptySet())?.toSet() ?: emptySet()

    fun setSelectedSlugs(ctx: Context, slugs: Set<String>) {
        prefs(ctx).edit().putStringSet(KEY_CATEGORIES, slugs).apply()
    }

    /** Selected slugs in catalog order (stable for search URLs). */
    fun getOrderedSelectedSlugs(ctx: Context): List<String> {
        val selected = getSelectedSlugs(ctx)
        if (selected.isEmpty()) return emptyList()
        val catalog = Categories.forOrientation(getOrientation(ctx))
        return catalog.map { it.slug }.filter { it in selected }
    }
}
