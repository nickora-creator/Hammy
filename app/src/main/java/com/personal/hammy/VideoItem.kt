package com.personal.hammy

data class VideoItem(
    val title: String,
    val thumbUrl: String,
    val pageUrl: String,
    val durationLabel: String = ""
)
