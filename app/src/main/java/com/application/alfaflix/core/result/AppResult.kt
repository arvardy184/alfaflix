package com.application.alfaflix.core.result

/**
 * A failure the user can be told about, decoupled from the exception that caused it.
 *
 * Technical detail (stack traces, HTTP codes, exception names) stops here. The
 * presentation layer maps these cases to localized strings; nothing lower in the stack
 * decides what a user reads.
 */
sealed interface AppError {

    /** No usable network connection. */
    data object Offline : AppError

    /** The request was issued but the server did not answer in time. */
    data object Timeout : AppError

    /** The server answered with a failing HTTP status. */
    data class Server(val code: Int) : AppError

    /** The requested resource does not exist. Retrying will not help. */
    data object NotFound : AppError

    /**
     * No TMDB access token is configured. Self-diagnosing for anyone who clones the
     * repository without completing setup, instead of a confusing generic failure.
     */
    data object MissingApiKey : AppError

    /** A token is configured but TMDB rejected it (HTTP 401). */
    data object InvalidApiKey : AppError

    /** Too many requests (HTTP 429); worth retrying, but not immediately. */
    data object RateLimited : AppError

    /** Anything unclassified, including local storage failures. */
    data object Unknown : AppError
}

/**
 * The outcome of an operation that can fail in a way worth showing the user.
 *
 * Preferred over [kotlin.Result] because failures here are a closed, exhaustively
 * matchable set rather than an arbitrary [Throwable].
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}
