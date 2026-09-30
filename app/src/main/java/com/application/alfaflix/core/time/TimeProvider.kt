package com.application.alfaflix.core.time

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the current time.
 *
 * Injected rather than calling [System.currentTimeMillis] directly so cache-TTL logic can
 * be tested deterministically: a test can place "now" eight days past a cached timestamp
 * without sleeping or manipulating the system clock.
 */
interface TimeProvider {
    fun nowMillis(): Long
}

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
