package com.application.alfaflix.data.repository

import app.cash.turbine.test
import com.application.alfaflix.core.result.AppError
import com.application.alfaflix.core.result.AppResult
import com.application.alfaflix.core.result.RefreshState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tracker's promises are what keep "refreshing" honest: never stuck, never overwritten by
 * an older operation, and never duplicated when exclusivity is requested.
 */
class RefreshTrackerTest {

    private val tracker = RefreshTracker<String>()

    private suspend fun stateOf(key: String) = tracker.observe(key).first()

    @Test
    fun `a successful operation goes back to idle`() = runTest {
        tracker.track("k") { AppResult.Success(Unit) }

        assertEquals(RefreshState.Idle, stateOf("k"))
    }

    @Test
    fun `a failed operation records the failure`() = runTest {
        val result = tracker.track("k") { AppResult.Failure(AppError.Offline) }

        assertEquals(AppResult.Failure(AppError.Offline), result)
        assertEquals(RefreshState.Failed(AppError.Offline), stateOf("k"))
    }

    @Test
    fun `state is in progress while the operation runs`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val job = launch { tracker.track("k") { gate.await(); AppResult.Success(Unit) } }
        testScheduler.runCurrent()

        assertEquals(RefreshState.InProgress, stateOf("k"))

        gate.complete(Unit)
        job.join()
        assertEquals(RefreshState.Idle, stateOf("k"))
    }

    @Test
    fun `a cancelled operation cannot leave the key refreshing`() = runTest {
        val job = launch { tracker.track("k") { CompletableDeferred<Unit>().await(); AppResult.Success(Unit) } }
        testScheduler.runCurrent()
        assertEquals(RefreshState.InProgress, stateOf("k"))

        job.cancel()
        job.join()

        assertEquals(RefreshState.Idle, stateOf("k"))
    }

    @Test
    fun `an exception inside the operation becomes a failure instead of crashing or sticking`() = runTest {
        val result = tracker.track("k") { error("database is locked") }

        assertEquals(AppResult.Failure(AppError.Unknown), result)
        assertEquals(RefreshState.Failed(AppError.Unknown), stateOf("k"))
    }

    @Test
    fun `an older operation finishing late cannot overwrite a newer one's state`() = runTest {
        val oldGate = CompletableDeferred<Unit>()
        val newGate = CompletableDeferred<Unit>()

        val old = launch { tracker.track("k") { oldGate.await(); AppResult.Failure(AppError.Timeout) } }
        testScheduler.runCurrent()
        val new = launch { tracker.track("k") { newGate.await(); AppResult.Success(Unit) } }
        testScheduler.runCurrent()

        // The newer operation is still running; the older one now finishes with a failure.
        oldGate.complete(Unit)
        old.join()

        assertEquals(
            "the stale failure must not replace the newer operation's in-progress state",
            RefreshState.InProgress,
            stateOf("k"),
        )

        newGate.complete(Unit)
        new.join()
        assertEquals(RefreshState.Idle, stateOf("k"))
    }

    @Test
    fun `cancelling an older operation does not clear a newer one's state`() = runTest {
        val old = launch { tracker.track("k") { CompletableDeferred<Unit>().await(); AppResult.Success(Unit) } }
        testScheduler.runCurrent()
        val newGate = CompletableDeferred<Unit>()
        val new = launch { tracker.track("k") { newGate.await(); AppResult.Success(Unit) } }
        testScheduler.runCurrent()

        old.cancel()
        old.join()

        assertEquals(RefreshState.InProgress, stateOf("k"))
        newGate.complete(Unit)
        new.join()
    }

    @Test
    fun `an exclusive call while one is running does not run its block`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var runs = 0
        val first = launch { tracker.track("k", exclusive = true) { runs++; gate.await(); AppResult.Success(Unit) } }
        testScheduler.runCurrent()

        val second = tracker.track("k", exclusive = true) { runs++; AppResult.Success(Unit) }

        assertEquals("the duplicate must be skipped", 1, runs)
        assertEquals(AppResult.Success(Unit), second)
        gate.complete(Unit)
        first.join()
    }

    @Test
    fun `an exclusive call runs again once the previous one has finished`() = runTest {
        var runs = 0
        tracker.track("k", exclusive = true) { runs++; AppResult.Success(Unit) }
        tracker.track("k", exclusive = true) { runs++; AppResult.Success(Unit) }

        assertEquals(2, runs)
    }

    @Test
    fun `keys are independent`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val job = async(start = CoroutineStart.UNDISPATCHED) {
            tracker.track("a") { gate.await(); AppResult.Success(Unit) }
        }

        assertEquals(RefreshState.InProgress, stateOf("a"))
        assertEquals(RefreshState.Idle, stateOf("b"))
        gate.complete(Unit)
        job.await()
    }

    @Test
    fun `observers see progress then completion`() = runTest {
        val gate = CompletableDeferred<Unit>()
        tracker.observe("k").test {
            assertEquals(RefreshState.Idle, awaitItem())

            val job = launch { tracker.track("k") { gate.await(); AppResult.Success(Unit) } }
            assertEquals(RefreshState.InProgress, awaitItem())

            gate.complete(Unit)
            job.join()
            assertEquals(RefreshState.Idle, awaitItem())
            assertTrue(cancelAndConsumeRemainingEvents().isEmpty())
        }
    }
}
