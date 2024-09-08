/*
 * SPDX-FileCopyrightText: 2024 Daniel Getz <dan@getzit.net>
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.getzit.repti

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class IdleTimeoutTriggerTest {
    @Test
    fun testTriggerRunsOnce() = runBlocking {
        val runs = AtomicInteger(0)
        val timeout = IdleTimeoutTrigger<Unit>(100, scope = this) {
            runs.getAndIncrement()
        }
        timeout.trigger(Unit)
        delay(500.milliseconds)
        assertEquals(1, runs.get())
    }

    @Test
    fun testTriggerRunsWithValue() = runBlocking {
        val value = AtomicInteger(0)
        val timeout = IdleTimeoutTrigger<Int>(100, scope = this) {
            value.set(it)
        }
        timeout.trigger(3)
        delay(500.milliseconds)
        assertEquals(3, value.get())
    }

    @Test
    fun testTriggerRunsOnceWithLastValue() = runBlocking {
        val values = mutableListOf<Int>()
        val timeout = IdleTimeoutTrigger<Int>(100, scope = this) {
            values.add(it)
        }
        timeout.trigger(10)
        timeout.trigger(20)
        delay(500.milliseconds)
        assertEquals(listOf(20), values)
    }

    @Test
    fun testRunNowIfTriggered_NotTriggered() = runBlocking {
        val runs = AtomicInteger(0)
        val timeout = IdleTimeoutTrigger<Unit>(100, scope = this) {
            runs.getAndIncrement()
        }
        timeout.runNowIfTriggered()
        delay(500.milliseconds)
        assertEquals(0, runs.get())
    }

    @Test
    fun testRunNowIfTriggered_Triggered() = runBlocking {
        val runs = AtomicInteger(0)
        val timeout = IdleTimeoutTrigger<Unit>(1000, scope = this) {
            runs.getAndIncrement()
        }
        timeout.trigger(Unit)
        timeout.runNowIfTriggered()
        assertEquals(1, runs.get())
        delay(500.milliseconds)
        assertEquals(1, runs.get())
    }
}