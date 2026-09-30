package com.application.alfaflix.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Replaces the main dispatcher so `viewModelScope` runs on a scheduler the test controls.
 *
 * The scheduler is exposed and handed to `runTest` so both share one clock — that is what
 * lets a test advance past a 400 ms debounce instantly instead of really waiting.
 */
class MainDispatcherRule(
    val scheduler: TestCoroutineScheduler = TestCoroutineScheduler(),
) : TestWatcher() {

    private val dispatcher = StandardTestDispatcher(scheduler)

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
