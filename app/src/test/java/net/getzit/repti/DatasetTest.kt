package net.getzit.repti

import io.kotest.common.runBlocking
import io.kotest.property.*
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.localDate
import io.kotest.property.arbitrary.localDateTime
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.uShort
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

class DatasetTest {
    private val dayArb = Arb.localDate(
        LocalDate.of(2023, 1, 1),
        LocalDate.of(2040, 1, 1)
    ).map(Day::of)

    private val ldtArb = Arb.localDateTime(2023, 2040)

    private val timestampArb = ldtArb.map { Timestamp.of(it.toInstant(ZoneOffset.UTC)) }

    private val clockArb =
        ldtArb.map { Clock.fixed(it.toInstant(ZoneOffset.UTC), ZoneId.systemDefault()) }

    private inline fun <T> withClock(dataset: Dataset, clock: Clock, run: () -> T): T {
        val oldClock = dataset.clock
        try {
            dataset.clock = clock
            return run()
        } finally {
            dataset.clock = oldClock
        }
    }

    private val datasetArb = arbitrary {
        val dataset = Dataset()
        for (name in Arb.list(Arb.string()).bind()) {
            val task = withClock(dataset, clockArb.bind()) {
                dataset.newTask(name)
            }
            withClock(dataset, clockArb.bind()) {
                when (Arb.int(1, 3).bind()) {
                    1 -> task.delete()
                    2 -> task.done = dayArb.bind()
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
        private val zoneId: ZoneId = ZoneId.systemDefault()
    ) : Clock() {
        override fun instant() = instant
        override fun withZone(newZoneId: ZoneId?): Clock = MockClock(instant, newZoneId!!)
        override fun getZone(): ZoneId = zoneId
    }

    /**
     * An action that modifies a [Dataset]
     */
    private interface DatasetMod {
        fun run(dataset: Dataset)
        val timeInc: Duration
    }

    private abstract class DatasetIdxMod : DatasetMod {
        protected abstract fun runOnTask(task: Task)

        abstract val idx: UShort

        override fun run(dataset: Dataset) {
            val tasks = dataset.allTasks.toList()
            if (tasks.isNotEmpty()) {
                runOnTask(tasks[(idx % tasks.size.toUInt()).toInt()])
            }
        }
    }

    private data class DatasetModDelete(
        override val idx: UShort,
        override val timeInc: Duration
    ) : DatasetIdxMod() {
        override fun runOnTask(task: Task) {
            task.delete()
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
        override fun runOnTask(task: Task) {
            task.name = name
        }
    }

    private data class DatasetModSetTaskDone(
        override val idx: UShort,
        val done: Day?,
        override val timeInc: Duration
    ) : DatasetIdxMod() {
        override fun runOnTask(task: Task) {
            task.done = done
        }
    }

    private val datasetModArb: Arb<DatasetMod> = arbitrary {
        val timeInc: Duration = Duration.ofMillis(Arb.long(0..2_000_000_000L).bind())
        when (Arb.int(1, 4).bind()) {
            1 -> DatasetModNewTask(Arb.string().bind(), timeInc)
            2 -> DatasetModRenameTask(Arb.uShort().bind(), Arb.string().bind(), timeInc)
            3 -> DatasetModSetTaskDone(Arb.uShort().bind(), dayArb.bind(), timeInc)
            else -> DatasetModDelete(Arb.uShort().bind(), timeInc)
        }
    }

    private fun assertDatasetsEqual(d1: Dataset, d2: Dataset) {
        assertEquals(Json.encodeToJsonElement(d1), Json.encodeToJsonElement(d2))
    }

    private fun assertDatasetsNotEqual(d1: Dataset, d2: Dataset) {
        assertNotEquals(Json.encodeToJsonElement(d1), Json.encodeToJsonElement(d2))
    }

    private fun datasetSerCopy(d: Dataset): Dataset =
        Json.decodeFromJsonElement(Json.encodeToJsonElement(d))

    @Test
    fun testDatasetJsonEncodeDecode() {
        runBlocking {
            checkAll(datasetArb) { dataset ->
                assertDatasetsEqual(dataset, Json.decodeFromString(Json.encodeToString(dataset)))
            }
        }
    }

    @Test
    fun testNewTask() {
        runBlocking {
            checkAll(datasetArb) { dataset ->
                val task = dataset.newTask("test")
                assertTrue(task.dataset == dataset)
                assertTrue(task.name == "test")
                assertNull(task.done)
                assertTrue(dataset.allTasks.contains(task))
            }
        }
    }

    @Test
    fun testDelete() {
        runBlocking {
            checkAll(datasetArb) { dataset ->
                val task = dataset.newTask("test")
                val datasetCopy = dataset.copy()
                task.delete()
                assertFalse(dataset.allTasks.contains(task))
                assertTrue(task.id in datasetCopy.tasksById)
            }
        }
    }

    @Test
    fun testDatasetCopy() {
        runBlocking {
            checkAll(datasetArb) { dataset ->
                val copy = dataset.copy()
                assertDatasetsEqual(dataset, copy)

                // check that there are no references between the two Datasets
                val origTasks = dataset.allTasks
                for (task in copy.allTasks) {
                    assertFalse(task.dataset == dataset)
                    assertFalse(task in origTasks)
                }
            }
        }
    }

    @Test
    fun testRenameTask() {
        runBlocking {
            checkAll(datasetArb, Arb.string()) { dataset, newName ->
                val task = dataset.newTask("before")
                val datasetCopy = dataset.copy()
                task.name = newName
                assertDatasetsNotEqual(dataset, datasetCopy)
                assertEquals(newName, datasetSerCopy(dataset).tasksById[task.id]?.name)
            }
        }
    }

    @Test
    fun testSetTaskDone() {
        runBlocking {
            checkAll(datasetArb, dayArb.orNull(0.1)) { dataset, day ->
                val task = dataset.newTask("any name")
                val datasetCopy = dataset.copy()
                task.done = day
                assertDatasetsNotEqual(dataset, datasetCopy)
                assertEquals(day, datasetSerCopy(dataset).tasksById[task.id]!!.done)
            }
        }
    }

    @Test
    fun testUpdateFromUnrelatedCommutative() {
        runBlocking {
            checkAll(datasetArb, datasetArb) { d1, d2 ->
                assertDatasetsEqual(
                    d1.copy().also { it.updateFrom(d2) },
                    d2.copy().also { it.updateFrom(d1) })
            }
        }
    }

    private fun maxDatasetTime(dataset: Dataset): Instant =
        ((dataset.deletedTasks.values.asSequence() +
                dataset.allTasks.asSequence().flatMap { it.timestamps.values })
            .map { it.asInstant })
            .maxOrNull() ?: Instant.ofEpochSecond(1609459200)

    @Test
    fun testUpdateFromRelatedCommutative() {
        runBlocking {
            checkAll(
                datasetArb,
                Arb.list(datasetModArb),
                Arb.list(datasetModArb)
            ) { dataset1, mods1, mods2 ->
                val startTime = maxDatasetTime(dataset1)
                val dataset2 = dataset1.copy()
                val datasets = listOf(dataset1, dataset2)
                val mods = listOf(mods1, mods2)
                val clocks = listOf(MockClock(startTime), MockClock(startTime))
                for (i in 0..1) {
                    datasets[i].clock = clocks[i]
                    for (mod in mods[i]) {
                        mod.run(datasets[i])
                        clocks[i].instant += mod.timeInc
                    }
                }
                assertDatasetsEqual(
                    dataset1.copy().also { it.updateFrom(dataset2) },
                    dataset2.copy().also { it.updateFrom(dataset1) })
            }
        }
    }

    @Test
    fun testUpdateFrom() {
        runBlocking {
            checkAll(datasetArb, datasetModArb) { d1, mod ->
                val d2 = d1.copy()
                d1.clock = Clock.fixed(maxDatasetTime(d1) + mod.timeInc, ZoneId.systemDefault())
                mod.run(d1)
                d2.updateFrom(d1)
                assertDatasetsEqual(d1, d2)
            }
        }
    }

    @Test
    fun testTimestampOfAsInstant() {
        runBlocking {
            checkAll<Timestamp> { t ->
                assertEquals(t, Timestamp.of(t.asInstant))
            }
        }
    }

    @Test
    fun testDayOfDate() {
        runBlocking {
            checkAll<Day> { d ->
                assertEquals(d, Day.of(d.date))
            }
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
                ids.add(TaskId.random(random))
            }
            // check that there were no more than 2 collisions
            assertTrue(ids.size > numIds - 2)
        }
    }
}