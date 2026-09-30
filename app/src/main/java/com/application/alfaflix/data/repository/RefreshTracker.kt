package com.application.alfaflix.data.repository

import com.application.alfaflix.core.result.AppError
import com.application.alfaflix.core.result.AppResult
import com.application.alfaflix.core.result.RefreshState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory refresh state per key.
 *
 * Refresh state describes an in-flight operation, not a fact about the data, so it is never
 * persisted — a flag saved to disk would be stranded as "refreshing" if the process died
 * mid-request.
 *
 * Every [track]ed operation owns a generation. Only the operation holding the *current*
 * generation may write a final state, which gives two guarantees:
 *
 *  - A cancelled or failed operation can never leave the key stuck at
 *    [RefreshState.InProgress].
 *  - An older operation finishing late can never overwrite the state of a newer one.
 *
 * With `exclusive = true` a second call while one is running does nothing, which is how
 * duplicate concurrent page requests are prevented.
 */
class RefreshTracker<K : Any> {

    private data class Entry(val generation: Long, val state: RefreshState)

    private val entries = MutableStateFlow<Map<K, Entry>>(emptyMap())
    private val generations = AtomicLong()

    fun observe(key: K): Flow<RefreshState> =
        entries.map { it[key]?.state ?: RefreshState.Idle }.distinctUntilChanged()

    /**
     * Runs [block] as a tracked operation.
     *
     * @return the block's result. A skipped exclusive call reports [AppResult.Success]:
     *   the work it would have done is already in progress.
     */
    suspend fun track(
        key: K,
        exclusive: Boolean = false,
        block: suspend () -> AppResult<Unit>,
    ): AppResult<Unit> {
        val generation = begin(key, exclusive) ?: return AppResult.Success(Unit)

        val result = try {
            block()
        } catch (cancellation: CancellationException) {
            // Cancellation is not a failure: release the key and let it propagate.
            finish(key, generation, RefreshState.Idle)
            throw cancellation
        } catch (_: Exception) {
            // Local failures (a database write, say) must surface as a failed refresh,
            // not crash the caller or strand the key at InProgress.
            AppResult.Failure(AppError.Unknown)
        }

        finish(
            key,
            generation,
            when (result) {
                is AppResult.Success -> RefreshState.Idle
                is AppResult.Failure -> RefreshState.Failed(result.error)
            },
        )
        return result
    }

    private fun begin(key: K, exclusive: Boolean): Long? {
        val generation = generations.incrementAndGet()
        var started = false
        entries.update { current ->
            if (exclusive && current[key]?.state == RefreshState.InProgress) {
                started = false
                current
            } else {
                started = true
                current + (key to Entry(generation, RefreshState.InProgress))
            }
        }
        return generation.takeIf { started }
    }

    private fun finish(key: K, generation: Long, state: RefreshState) {
        entries.update { current ->
            if (current[key]?.generation == generation) {
                current + (key to Entry(generation, state))
            } else {
                current
            }
        }
    }
}
