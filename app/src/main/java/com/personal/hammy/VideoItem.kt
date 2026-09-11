package com.personal.hammy

data class VideoItem(
    val title: String,
    val thumbUrl: String,
    val pageUrl: String,
    val durationLabel: String = "",
    /** Category/tag slugs or names from listing metadata when available. */
    val tags: Set<String> = emptySet()
)
