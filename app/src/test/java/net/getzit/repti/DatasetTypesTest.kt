// SPDX-FileCopyrightText: 2024-2025 Daniel Getz <dan@getzit.net>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package net.getzit.repti

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.common.runBlocking
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.localDate
import io.kotest.property.arbitrary.localDateTime
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.withEdgecases
import io.kotest.property.checkAll
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.random.Random

class DatasetTypesTest {
    private val localDateArb = Arb.localDate(LocalDate.of(2020, 1, 1), LocalDate.of(2050, 1, 1))

    private val ldtArb = Arb.localDateTime(2020, 2050)

    private val instantArb = ldtArb.map { it.toInstant(ZoneOffset.UTC) }

    private val dayArb =
        Arb.long(-365L * 100, 365L * 1000)
            .withEdgecases(0L, Int.MAX_VALUE + 1L)
            .map(::Day)

    private val timestampArb =
        Arb.long(-365L * 100 * 24 * 60 * 60, 365L * 1000 * 24 * 60 * 60)
            .withEdgecases(0L, Int.MAX_VALUE + 1L)
            .map(::Timestamp)

    @Test
    fun testTimestampOfAsInstant() {
        runBlocking {
            checkAll(timestampArb) { t ->
                assertEquals(t, Timestamp.of(t.asInstant))
            }
        }
    }

    @Test
    fun testInstantToTimestamp() {
        runBlocking {
            checkAll(instantArb) { instant ->
                assertEquals(instant, Timestamp.of(instant).asInstant)
            }
        }
    }

    @Test
    fun testDayOfDate() {
        runBlocking {
            checkAll(dayArb) { day ->
                assertEquals(day, Day.of(day.date))
            }
        }
    }

    @Test
    fun testDateToDay() {
        runBlocking {
            checkAll(localDateArb) { date ->
                assertEquals(date, Day.of(date).date)
            }
        }
    }

    @Test
    fun testToFromMillis() {
        runBlocking {
            checkAll(dayArb) { day ->
                assertEquals(day, Day.fromMillis(day.millis))
            }
        }
    }

    @Test
    fun testMillis() {
        runBlocking {
            checkAll(localDateArb) { date ->
                assertEquals(
                    date.atTime(LocalTime.MIN).toInstant(ZoneOffset.UTC).toEpochMilli(),
                    Day.of(date).millis
                )
            }
        }
    }

    @Test
    fun testFromMillis() {
        runBlocking {
            checkAll(localDateArb) { date ->
                assertEquals(
                    date,
                    Day.fromMillis(
                        date.atTime(LocalTime.MIN).toInstant(ZoneOffset.UTC).toEpochMilli()
                    ).date
                )
            }
        }
    }

    @Test
    fun testDatePlusAfter(): Unit = runBlocking {
        checkAll(localDateArb.map(Day::of), Arb.int(-10_000, 10_000)) { day, delta ->
            assertEquals(delta.toLong(), day.plusDays(delta).daysAfter(day))
        }
    }

    @Test
    fun testDateMinusAfter(): Unit = runBlocking {
        checkAll(localDateArb.map(Day::of), Arb.int(-10_000, 10_000)) { day, delta ->
            assertEquals(delta.toLong(), day.daysAfter(day.minusDays(delta)))
        }
    }

    @Test
    fun testTaskIdSerializeEquals() {
        runBlocking {
            checkAll<TaskId> { id ->
                assertEquals(id, Json.decodeFromString<TaskId>(Json.encodeToString(id)))
            }
        }
    }

    @Test
    fun testTaskIdRandom() {
        val random = Random(0x1234567890baabaaL)
        val ids = mutableSetOf<TaskId>()
        val numIds = 2000
        for (iRun in 1..10_000) {
            ids.clear()
            for (iId in 1..numIds) {
                ids.add(TaskId.random(random).also { assertTrue(it.valid) })
            }
            // check that there were no more than 2 collisions
            assertTrue(ids.size > numIds - 2)
        }
    }

    @Test
    fun testInvalidTaskIds() {
        assertFalse(TaskId("").valid)
        assertFalse(TaskId("a").valid)
        assertFalse(TaskId("0").valid)
        assertFalse(TaskId("muchmuchtoolong").valid)
        assertFalse(TaskId("123456é").valid)
        assertFalse(TaskId("A234567").valid)
    }
}

@RunWith(AndroidJUnit4::class)
class DatasetTypesParcelableTest {
    @Test
    fun testTaskIdInBundle() {
        val key = "key"
        runBlocking {
            checkAll<TaskId> { id ->
                val bundle = Bundle()
                bundle.putParcelable(key, id)
                assertEquals(id, bundle.getParcelable(key, TaskId::class.java))
            }
        }
    }
}
