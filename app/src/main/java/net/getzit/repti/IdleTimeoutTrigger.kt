/*
 * SPDX-FileCopyrightText: 2024 Daniel Getz <dan@getzit.net>
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.getzit.repti

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Class to run a given coroutine multiple times, if and only if triggered, but only after a timeout.
 *
 * After [trigger] is called, [onTimeout] is scheduled to be called on a value of type [T],
 * in [scope], at least [timeoutMillis] milliseconds later.
 * If it was already scheduled, it will be rescheduled so that it's once again
 * at least [timeoutMillis] seconds later.
 */
class IdleTimeoutTrigger<T>(
    private val timeoutMillis: Long,
    private val scope: CoroutineScope,
    val onTimeout: suspend (T) -> Unit
) {
    private val mutex = Mutex()
    private var value: T? = null
    private var waitLonger = false

    /**
     * Schedules the timeout, or reschedules it to be later, and sets the value that the timeout
     * will be called on to [value], replacing any previous value given.
     */
    suspend fun trigger(value: T) {
        mutex.withLock {
            if (this.value != null) {
                waitLonger = true
            } else {
                scope.launch { doTimeout() }
            }
            this.value = value
        }
    }

    /**
     * Provides a hint to run the timeout sooner, rather than later.
     */
    suspend fun hurryUp() {
        mutex.withLock {
            waitLonger = false
        }
    }

    private suspend fun doTimeout() {
        while (true) {
            delay(timeoutMillis)
            mutex.withLock {
                if (waitLonger) {
                    waitLonger = false
                } else {
                    val value = value
                    if (value != null) {
                        onTimeout(value)
                        this.value = null
                    }
                    return
                }
            }
        }
    }

    /**
     * Run [onTimeout] immediately, if [trigger] had been called.
     * It will not be run again unless [trigger] is called again.
     */
    suspend fun runNowIfTriggered() {
        mutex.withLock {
            val value = value
            if (value != null) {
                onTimeout(value)
                this.value = null
            }
            waitLonger = false
        }
    }
}