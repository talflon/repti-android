// SPDX-FileCopyrightText: 2024 Daniel Getz <dan@getzit.net>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package net.getzit.repti

import io.kotest.common.runBlocking
import io.kotest.property.Arb
import io.kotest.property.Shrinker
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.localDate
import io.kotest.property.arbitrary.localDateTime
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.uShort
import io.kotest.property.assume
import io.kotest.property.checkAll
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAmount
import kotlin.random.Random

class DatasetTest {
    private val dayArb = Arb.localDate(
        LocalDate.of(2023, 1, 1),
        LocalDate.of(2040, 1, 1)
    ).map(Day::of)

    private val ldtArb = Arb.localDateTime(2023, 2040)

    private val timestampArb = ldtArb.map { Timestamp.of(it.toInstant(ZoneOffset.UTC)) }

    private val clockArb =
        ldtArb.map { Clock.fixed(it.toInstant(ZoneOffset.UTC), ZoneOffset.UTC) }

    private val smallNameArb = Arb.string(0, 5, Codepoint.alphanumeric())

    private inline fun <T> Dataset.withClock(clock: Clock, run: (Dataset) -> T): T {
        val oldClock = this.clock
        try {
            this.clock = clock
            return run(this)
        } finally {
            this.clock = oldClock
        }
    }

    /**
     * Eliminate a task from a Dataset, without registering a deletion.
     */
    private fun erase(dataset: Dataset, id: TaskId) {
        dataset.tasks -= id
        dataset.updates -= id
        dataset.deleted -= id
        dataset.order -= id
    }

    /**
     * Shrinks by removing and shortening lots of tasks in one go
     */
    private fun addFastShrinks(dataset: Dataset, shrinks: MutableList<Dataset>) {
        for (speedPct in 80 downTo 20 step 10) {
            val newDataset = dataset.copy()
            for ((id, task) in dataset.tasks) {
                if (Random.nextInt(100) < speedPct) {
                    if (Random.nextBoolean()) {
                        // eliminate without registering a deletion, so that the Dataset is truly smaller
                        erase(newDataset, id)
                    } else if (task.name.length > 1) {
                        // reduce length of name
                        newDataset.tasks[id] =
                            task.copy(name = task.name.substring(0..task.name.length / 2))
                    }
                }
            }
            for (id in dataset.deleted.keys) {
                if (Random.nextInt(100) < speedPct) {
                    erase(newDataset, id)
                }
            }
            if (newDataset.toString() != dataset.toString()) {
                shrinks.add(newDataset)
            }
        }
    }

    /**
     * Shrinks by individual changes to Dataset
     */
    private fun addSlowShrinks(dataset: Dataset, shrinks: MutableList<Dataset>) {
        for ((id, task) in dataset.tasks) {
            shrinks.add(dataset.copy().also { erase(it, id) })
            if (task.name.length > 1) {
                shrinks.add(dataset.copy().also {
                    it.tasks[id] = task.copy(name = task.name.substring(0..task.name.length / 2))
                })
            }
        }
        for (id in dataset.deleted.keys) {
            shrinks.add(dataset.copy().also { erase(it, id) })
        }
    }

    private val slowDatasetShrinker = Shrinker<Dataset> { dataset ->
        val shrunk = mutableListOf<Dataset>()
        addSlowShrinks(dataset, shrunk)
        shrunk
    }

    private val datasetShrinker = Shrinker<Dataset> { dataset ->
        val shrunk = mutableListOf<Dataset>()
        addFastShrinks(dataset, shrunk)
        addSlowShrinks(dataset, shrunk)
        shrunk
    }

    private fun datasetArb(nameArb: Arb<String>) = arbitrary(datasetShrinker) {
        val dataset = Dataset()
        for (name in Arb.list(nameArb).bind()) {
            val task = dataset.withClock(clockArb.bind()) {
                dataset.newTask(name)
            }
            dataset.withClock(clockArb.bind()) {
                when (Arb.int(1, 3).bind()) {
                    1 -> dataset.delete(task)
                    2 -> dataset.update(task.copy(done = dayArb.bind()))
                }
            }
        }
        dataset
    }

    /**
     * [Clock] with mutable instant
     * @param instant the initial [Instant]
     * @zoneId zoneId the [ZoneId] to use, defaults to [ZoneId.systemDefault]
     */
    private data class MockClock(
        var instant: Instant,
        private val zoneId: ZoneId = ZoneOffset.UTC
    ) : Clock() {
        override fun instant() = instant
        override fun withZone(newZoneId: ZoneId?): Clock = MockClock(instant, newZoneId!!)
        override fun getZone(): ZoneId = zoneId

        fun tick(seconds: Long = 1) {
            instant += Duration.ofSeconds(seconds)
        }

        fun tick(temporalAmount: TemporalAmount) {
            instant += temporalAmount
        }

        companion object {
            fun of(baseTime: LocalDateTime) = MockClock(baseTime.toInstant(ZoneOffset.UTC))

            fun ofEpochSeconds(baseTime: Long) = MockClock(Instant.ofEpochSecond(baseTime))

            fun default(): MockClock = ofEpochSeconds(1700000000L)
        }
    }

    /**
     * An action that modifies a [Dataset]
     */
    private interface DatasetMod {
        fun run(dataset: Dataset)
        val timeInc: Duration
    }

    private abstract class DatasetIdxMod : DatasetMod {
        protected abstract fun runOnTask(dataset: Dataset, task: Task)

        abstract val idx: UShort

        override fun run(dataset: Dataset) {
            val tasks = dataset.allTasks.toList()
            if (tasks.isNotEmpty()) {
                runOnTask(dataset, taskByIdx(dataset, idx))
            }
        }
    }

    private data class DatasetModDelete(
        override val idx: UShort,
        override val timeInc: Duration
    ) : DatasetIdxMod() {
        override fun runOnTask(dataset: Dataset, task: Task) {
            dataset.delete(task)
        }
    }

    private data class DatasetModNewTask(
        val name: String,
        override val timeInc: Duration
    ) : DatasetMod {
        override fun run(dataset: Dataset) {
            dataset.newTask(name)
        }
    }

    private data class DatasetModRenameTask(
        override val idx: UShort,
        val name: String,
        override val timeInc: Duration
    ) : DatasetIdxMod() {
        override fun runOnTask(dataset: Dataset, task: Task) {
            dataset.update(task.copy(name = this.name))
        }
    }

    private data class DatasetModSetTaskDone(
        override val idx: UShort,
        val done: Day?,
        override val timeInc: Duration
    ) : DatasetIdxMod() {
        override fun runOnTask(dataset: Dataset, task: Task) {
            dataset.update(task.copy(done = done))
        }
    }

    private data class DatasetModMoveTaskAfter(
        val moveIdx: UShort,
        val afterIdx: UShort?,
        override val timeInc: Duration
    ) : DatasetMod {
        override fun run(dataset: Dataset) {
            if (dataset.tasks.isNotEmpty()) {
                dataset.moveTaskAfter(
                    taskByIdx(dataset, moveIdx).id,
                    afterTaskId = if (afterIdx == null) null else taskByIdx(dataset, afterIdx).id
                )
            }
        }
    }

    private data class DatasetModMoveTaskBefore(
        val moveIdx: UShort,
        val beforeIdx: UShort?,
        override val timeInc: Duration
    ) : DatasetMod {
        override fun run(dataset: Dataset) {
            if (dataset.tasks.isNotEmpty()) {
                dataset.moveTaskBefore(
                    taskByIdx(dataset, moveIdx).id,
                    beforeTaskId = if (beforeIdx == null) null else taskByIdx(dataset, beforeIdx).id
                )
            }
        }
    }

    private fun datasetModArb(nameArb: Arb<String>): Arb<DatasetMod> = arbitrary {
        val timeInc: Duration = Duration.ofMillis(Arb.long(1_001L..100_000_000L).bind())
        when (Arb.int(1, 6).bind()) {
            1 -> DatasetModNewTask(nameArb.bind(), timeInc)
            2 -> DatasetModRenameTask(Arb.uShort().bind(), nameArb.bind(), timeInc)
            3 -> DatasetModSetTaskDone(Arb.uShort().bind(), dayArb.bind(), timeInc)
            4 -> DatasetModDelete(Arb.uShort().bind(), timeInc)
            5 -> DatasetModMoveTaskAfter(Arb.uShort().bind(), Arb.uShort().bind(), timeInc)
            else -> DatasetModMoveTaskBefore(Arb.uShort().bind(), Arb.uShort().bind(), timeInc)
        }
    }

    private val DATASET_MOD_ARB_CLASSES = listOf(
        DatasetModNewTask::class,
        DatasetModRenameTask::class,
        DatasetModSetTaskDone::class,
        DatasetModDelete::class,
        DatasetModMoveTaskAfter::class,
        DatasetModMoveTaskBefore::class,
    )

    // Tries to shrink quickly by removing all actions of the same type at once
    private val datasetModListShrinker = Shrinker<List<DatasetMod>> { modList ->
        val shrinks = mutableListOf<List<DatasetMod>>()
        if (modList.isNotEmpty()) {
            for (cls in DATASET_MOD_ARB_CLASSES) {
                val shrunk = modList.filterNot { it::class == cls }
                if (shrunk.size < modList.size)
                    shrinks += shrunk
            }
            // Slow backup shrinking, by removing individual items
            // Try the last and first ones first
            shrinks += modList.toMutableList().also { it.removeLast() }
            for (i in 0..<(modList.size - 1)) {
                shrinks += modList.toMutableList().also { it.removeAt(i) }
            }
        }
        shrinks
    }

    private val datasetModListArb = arbitrary(datasetModListShrinker) {
        Arb.list(datasetModArb(smallNameArb)).bind()
    }

    private fun datasetSerCopy(d: Dataset): Dataset =
        Json.decodeFromJsonElement(Json.encodeToJsonElement(d))

    @Test
    fun testDatasetJsonEncodeDecode() {
        runBlocking {
            checkAll(datasetArb(Arb.string())) { dataset ->
                assertDatasetsEqual(dataset, Json.decodeFromString(Json.encodeToString(dataset)))
                assertDatasetsEqual(dataset, Dataset.fromString(dataset.toString()))
            }
        }
    }

    @Test
    fun testNewTask() {
        runBlocking {
            checkAll(datasetArb(Arb.string())) { dataset ->
                val task = dataset.newTask("test")
                dataset.requireValid()
                assertTrue(task.name == "test")
                assertNull(task.done)
                assertTrue(dataset.allTasks.contains(task))
            }
        }
    }

    @Test
    fun testDelete() {
        runBlocking {
            checkAll(datasetArb(smallNameArb)) { dataset ->
                val task = dataset.newTask("test")
                val datasetCopy = dataset.copy()
                dataset.delete(task)
                dataset.requireValid()
                assertFalse(dataset.allTasks.contains(task))
                assertTrue(datasetCopy.allTasks.contains(task))
            }
        }
    }

    @Test
    fun testDatasetCopy() {
        runBlocking {
            checkAll(datasetArb(Arb.string())) { dataset ->
                val copy = dataset.copy()
                assertNotSame(dataset, copy)
                copy.requireValid()
                assertDatasetsEqual(dataset, copy)
            }
        }
    }

    @Test
    fun testCopyUpdatesNotLinked(): Unit = runBlocking {
        checkAll(datasetArb(Arb.string())) { dataset ->
            val copy = dataset.copy()
            assertNotSame(dataset.updates, copy.updates)
            for (id in dataset.tasks.keys) {
                assertNotSame(dataset.updates[id], copy.updates[id])
            }
        }
    }

    @Test
    fun testRenameTask() {
        runBlocking {
            checkAll(datasetArb(Arb.string()), Arb.string()) { dataset, newName ->
                val task = dataset.newTask("before $newName")
                val datasetCopy = dataset.copy()
                dataset.update(task.copy(name = newName))
                dataset.requireValid()
                assertDatasetsNotEqual(dataset, datasetCopy)
                assertEquals(newName, datasetSerCopy(dataset).getTask(task.id)?.name)
            }
        }
    }

    @Test
    fun testRenameTaskTimestamp() {
        val dataset = Dataset()
        val clock = MockClock.default()
        dataset.clock = clock
        val task = dataset.newTask("test")
        val oldTimestamp = dataset.updates[task.id]!!["name"]!!
        clock.tick(4857)
        dataset.update(task.copy(name = "different"))
        dataset.requireValid()
        val newTimestamp = dataset.updates[task.id]!!["name"]!!
        assertNotEquals(oldTimestamp, newTimestamp)
    }

    @Test
    fun testSetTaskDone() {
        runBlocking {
            checkAll(
                datasetArb(smallNameArb),
                dayArb.orNull(0.1),
                dayArb.orNull(0.1)
            ) { dataset, day1, day2 ->
                assume(day1 != day2)
                val task = dataset.newTask("any name")
                dataset.update(task.copy(done = day1))
                dataset.requireValid()
                val datasetCopy = dataset.copy()
                dataset.update(task.copy(done = day2))
                dataset.requireValid()
                assertDatasetsNotEqual(dataset, datasetCopy)
                assertEquals(day2, datasetSerCopy(dataset).getTask(task.id)!!.done)
            }
        }
    }

    private fun assertUpdateFromCommutative(dataset1: Dataset, dataset2: Dataset) {
        assertDatasetsEqual(
            dataset1.copy().also { it.updateFrom(dataset2); it.requireValid() },
            dataset2.copy().also { it.updateFrom(dataset1); it.requireValid() })
    }

    @Test
    fun testUpdateFromUnrelatedCommutative() {
        runBlocking {
            checkAll(datasetArb(smallNameArb), datasetArb(smallNameArb)) { d1, d2 ->
                assertUpdateFromCommutative(d1, d2)
            }
        }
    }

    private fun maxDatasetTime(dataset: Dataset): Instant? =
        (dataset.deleted.values.asSequence()
                + dataset.updates.values.asSequence().flatMap { it.values })
            .map { it.asInstant }.maxOrNull()

    private fun datasetTimeOrDefault(dataset: Dataset): Instant =
        maxDatasetTime(dataset) ?: LocalDateTime.of(2024, 1, 1, 0, 0).toInstant(ZoneOffset.UTC)

    private fun forkDataset(origDataset: Dataset, mods: List<List<DatasetMod>>): List<Dataset> {
        val datasets = List(mods.size) { origDataset.copy() }
        val startTime = datasetTimeOrDefault(origDataset)
        val clocks = List(mods.size) { MockClock(startTime) }
        for (i in mods.indices) {
            datasets[i].clock = clocks[i]
            for (mod in mods[i]) {
                clocks[i].tick(mod.timeInc)
                mod.run(datasets[i])
                datasets[i].requireValid()
            }
        }
        return datasets
    }

    @Test
    fun testUpdateFromRelatedCommutative() {
        runBlocking {
            checkAll(
                datasetArb(smallNameArb),
                datasetModListArb,
                datasetModListArb,
            ) { dataset1, mods1, mods2 ->
                val datasets = forkDataset(dataset1, listOf(mods1, mods2))
                assertUpdateFromCommutative(datasets[0], datasets[1])
            }
        }
    }

    @Test
    fun testUpdateFromCommutativeSimple() {
        val clock = MockClock.default()
        val d1 = Dataset()
        d1.clock = clock
        clock.tick()
        val task1 = d1.newTask("one")
        val d2 = d1.copy()
        d2.clock = clock
        clock.tick()
        val task2 = d2.newTask("two")
        assertUpdateFromCommutative(d1, d2)
        clock.tick()
        d1.update(task1.copy(name = "one!"))
        assertUpdateFromCommutative(d1, d2)
        clock.tick()
        d2.update(task1.copy(name = "one?"))
        assertUpdateFromCommutative(d1, d2)
        d1.update(task1.copy(name = "one!?"))
        assertUpdateFromCommutative(d1, d2)
        clock.tick()
        d2.delete(task1.id)
        assertUpdateFromCommutative(d1, d2)
        clock.tick()
        d2.delete(task2.id)
        assertUpdateFromCommutative(d1, d2)
    }

    @Test
    fun testUpdateFromCommutativeSimpleBoth() {
        val clock = MockClock.default()
        val d1 = Dataset()
        d1.clock = clock
        clock.tick()
        d1.newTask("one")
        val d2 = d1.copy().also { it.clock = clock }
        clock.tick()
        d2.newTask("two")
        clock.tick()
        d1.newTask("three")
        assertUpdateFromCommutative(d1, d2)
    }

    @Test
    fun testUpdateFromDescendantIdentical() {
        runBlocking {
            checkAll(datasetArb(smallNameArb), datasetModArb(smallNameArb)) { d1, mod ->
                val d2 = d1.copy()
                d1.clock = Clock.fixed(datasetTimeOrDefault(d1) + mod.timeInc, ZoneOffset.UTC)
                mod.run(d1)
                d2.updateFrom(d1)
                d2.requireValid()
                assertDatasetsEqual(d1, d2)
            }
        }
    }

    private fun assertUpdateFromIdempotent(toBeUpdated: Dataset, toUpdateFrom: Dataset) {
        assertDatasetsEqual(
            toBeUpdated.copy().also {
                it.updateFrom(toUpdateFrom)
                it.requireValid()
            },
            toBeUpdated.copy().also {
                it.updateFrom(toUpdateFrom)
                it.updateFrom(toUpdateFrom)
                it.requireValid()
            })
    }

    @Test
    fun testUpdateFromUnrelatedIdempotent() {
        runBlocking {
            checkAll(datasetArb(smallNameArb), datasetArb(smallNameArb)) { d1, d2 ->
                assertUpdateFromIdempotent(d1, d2)
            }
        }
    }

    @Test
    fun testUpdateFromRelatedIdempotent() {
        runBlocking {
            checkAll(
                datasetArb(smallNameArb),
                datasetModListArb,
                datasetModListArb,
            ) { dataset1, mods1, mods2 ->
                val datasets = forkDataset(dataset1, listOf(mods1, mods2))
                assertUpdateFromIdempotent(datasets[0], datasets[1])
            }
        }
    }

    @Test
    fun testUpdateFromEmpty() {
        runBlocking {
            checkAll(datasetModListArb.filterNot { it.isEmpty() }) { mods ->
                val d1 = Dataset()
                var now = datasetTimeOrDefault(d1)
                for (mod in mods) {
                    val d2 = d1.copy()
                    now += mod.timeInc
                    d1.clock = Clock.fixed(now, ZoneOffset.UTC)
                    mod.run(d1)
                    d1.requireValid()
                    d2.updateFrom(d1)
                    d2.requireValid()
                    assertDatasetsEqual(d1, d2)
                }
            }
        }
    }

    @Test
    fun testUpdateFromLeavesOtherUntouched() {
        runBlocking {
            checkAll(datasetArb(smallNameArb), datasetArb(smallNameArb)) { d1, d2 ->
                val copy = d2.copy()
                d1.updateFrom(d2)
                assertDatasetsEqual(d2, copy)
            }
        }
    }

    @Test
    fun testClear(): Unit = runBlocking {
        checkAll(datasetArb(Arb.string())) {
            it.clear()
            it.requireValid()
            assertTrue(it.allTasks.isEmpty())
        }
    }

    @Test
    fun testMoveTaskAfterNoChange() {
        runBlocking {
            checkAll(datasetArb(smallNameArb).filterNot { it.tasks.isEmpty() }) { dataset ->
                val orig = dataset.copy()
                for (i in orig.order.indices) {
                    dataset.moveTaskAfter(orig.order[i], orig.order.getOrNull(i - 1))
                    assertEquals(orig.order, dataset.order)
                }
            }
        }
    }

    @Test
    fun testMoveTaskBeforeNoChange() {
        runBlocking {
            checkAll(datasetArb(smallNameArb).filterNot { it.tasks.isEmpty() }) { dataset ->
                val orig = dataset.copy()
                for (i in orig.order.indices) {
                    dataset.moveTaskBefore(orig.order[i], orig.order.getOrNull(i + 1))
                    assertEquals(orig.order, dataset.order)
                }
            }
        }
    }

    @Test
    fun testMoveTaskBefore() {
        val dataset = Dataset()
        val tasks = List(4) { i -> dataset.newTask(i.toString()) }
        for (toMove in tasks) {
            for (toMoveBefore in tasks) {
                if (toMove != toMoveBefore) {
                    val copy = dataset.copy()
                    copy.moveTaskBefore(toMove.id, toMoveBefore.id)
                    val result = copy.allTasks
                    assertEquals(
                        result.indexOf(toMoveBefore) - 1,
                        result.indexOf(toMove)
                    )
                }
            }
        }
    }

    @Test
    fun testMoveTaskAfter() {
        val dataset = Dataset()
        val tasks = List(4) { i -> dataset.newTask(i.toString()) }
        for (toMove in tasks) {
            for (toMoveAfter in tasks) {
                if (toMove != toMoveAfter) {
                    val copy = dataset.copy()
                    copy.moveTaskAfter(toMove.id, toMoveAfter.id)
                    val result = copy.allTasks
                    assertEquals(
                        result.indexOf(toMoveAfter) + 1,
                        result.indexOf(toMove)
                    )
                }
            }
        }
    }
}

fun assertDatasetsEqual(d1: Dataset, d2: Dataset) {
    JSONAssert.assertEquals(Json.encodeToString(d1), Json.encodeToString(d2), true)
}

fun assertDatasetsNotEqual(d1: Dataset, d2: Dataset) {
    JSONAssert.assertNotEquals(Json.encodeToString(d1), Json.encodeToString(d2), true)
}

fun taskByIdx(dataset: Dataset, idx: UShort): Task =
    dataset.allTasks[idx.toInt() % dataset.tasks.size]
