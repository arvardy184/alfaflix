package com.application.alfaflix.ui.format

import java.text.DateFormat
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

private const val ISO_DATE_LENGTH = 10

/**
 * Formats the date part of an ISO-8601 string (`2021-06-23` or `2021-06-23T15:58:09Z`) in the
 * user's locale, e.g. "Jun 23, 2021".
 *
 * `java.time` needs API 26 and the app supports 25, so this uses `java.text`. Returns null
 * for anything unparseable so callers omit the label instead of showing garbage.
 */
fun formatIsoDate(iso: String?, locale: Locale = Locale.getDefault()): String? {
    val datePart = iso?.take(ISO_DATE_LENGTH)?.takeIf { it.length == ISO_DATE_LENGTH } ?: return null
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val date = parser.parse(datePart, ParsePosition(0)) ?: return null
    return DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(date)
}
