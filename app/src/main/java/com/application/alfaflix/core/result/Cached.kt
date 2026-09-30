package com.application.alfaflix.core.result

/** Progress of a background refresh running behind already-cached content. */
sealed interface RefreshState {
    data object Idle : RefreshState
    data object InProgress : RefreshState
    data class Failed(val error: AppError) : RefreshState
}

/**
 * Cached content plus the state of the refresh running behind it.
 *
 * Keeping [data] and [refresh] independent is what allows the UI to keep showing useful
 * content when a refresh fails, instead of replacing the screen with an error. The four
 * meaningful combinations are:
 *
 * - `data == null`, refreshing  → first load, show a skeleton
 * - `data == null`, failed      → nothing to show, show an error with retry
 * - `data != null`, failed      → keep the content, show a subtle "saved data" banner
 * - `data != null`, idle        → fresh content, no banner
 */
data class Cached<out T>(
    val data: T?,
    val lastUpdatedAt: Long?,
    val refresh: RefreshState = RefreshState.Idle,
) {
    val isRefreshing: Boolean get() = refresh == RefreshState.InProgress

    companion object {
        fun <T> empty(): Cached<T> = Cached(data = null, lastUpdatedAt = null)
    }
}
