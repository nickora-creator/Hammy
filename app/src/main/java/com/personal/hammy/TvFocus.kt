package com.personal.hammy

import android.view.View
import android.view.ViewGroup

/** Leanback-style focus: scale up + keep elevation so D-pad highlight is obvious. */
object TvFocus {
    private const val FOCUSED_SCALE = 1.08f
    private const val DURATION_MS = 120L

    fun attach(view: View, scale: Float = FOCUSED_SCALE) {
        view.isFocusable = true
        view.setOnFocusChangeListener { v, hasFocus ->
            v.animate()
                .scaleX(if (hasFocus) scale else 1f)
                .scaleY(if (hasFocus) scale else 1f)
                .translationZ(if (hasFocus) 8f else 0f)
                .setDuration(DURATION_MS)
                .start()
        }
    }

    fun attachRecursive(root: ViewGroup, vararg ids: Int) {
        for (id in ids) {
            root.findViewById<View>(id)?.let { attach(it) }
        }
    }
}
