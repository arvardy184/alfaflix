package com.application.alfaflix.core.result

/**
 * Why the content on screen is not fresh, and how old it is.
 *
 * Non-null only when cached content is being shown *and* the latest refresh did not
 * succeed. Fresh content carries null and shows no banner at all, so the messaging stays
 * unobtrusive by construction rather than by styling.
 */
data class Staleness(
    val lastUpdatedAt: Long,
    val reason: Reason,
) {
    enum class Reason {
        /** The device has no connection, so a refresh was never attempted. */
        OFFLINE,

        /** A refresh ran and failed. The cached content survived. */
        REFRESH_FAILED,
    }

    companion object {

        /**
         * Derives staleness from a cache snapshot, or null when there is nothing to say.
         */
        fun from(lastUpdatedAt: Long?, refresh: RefreshState, isOffline: Boolean): Staleness? {
            if (lastUpdatedAt == null) return null
            return when {
                refresh is RefreshState.Failed -> Staleness(
                    lastUpdatedAt = lastUpdatedAt,
                    reason = if (refresh.error == AppError.Offline || isOffline) {
                        Reason.OFFLINE
                    } else {
                        Reason.REFRESH_FAILED
                    },
                )

                isOffline -> Staleness(lastUpdatedAt, Reason.OFFLINE)
                else -> null
            }
        }
    }
}
