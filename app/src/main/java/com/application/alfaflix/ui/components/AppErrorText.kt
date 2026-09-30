package com.application.alfaflix.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.application.alfaflix.R
import com.application.alfaflix.core.result.AppError

/**
 * Turns a typed error into something a person can read.
 *
 * This is the *only* place errors become words, and it lives in the UI layer so every
 * message is a translatable resource. Nothing below this point decides what a user sees —
 * which is how exception names, HTTP codes and stack traces are kept off the screen.
 */
@Composable
fun AppError.asMessage(): String = when (this) {
    AppError.Offline -> stringResource(R.string.error_offline)
    AppError.Timeout -> stringResource(R.string.error_timeout)
    AppError.NotFound -> stringResource(R.string.error_not_found)
    AppError.MissingApiKey -> stringResource(R.string.error_missing_api_key)
    AppError.InvalidApiKey -> stringResource(R.string.error_invalid_api_key)
    AppError.RateLimited -> stringResource(R.string.error_rate_limited)
    is AppError.Server -> stringResource(R.string.error_server)
    AppError.Unknown -> stringResource(R.string.error_generic)
}

/**
 * Whether retrying could plausibly help.
 *
 * A missing or rejected token is fixed by editing configuration, not by tapping Retry, so
 * offering the button would just invite the user to fail again.
 */
val AppError.isRetryable: Boolean
    get() = when (this) {
        AppError.MissingApiKey, AppError.InvalidApiKey, AppError.NotFound -> false
        else -> true
    }
