package net.getzit.repti

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class LocalFileStorageTest {
    private val fileValues = listOf(
        "",
        "test",
        "\n",
        "\u0000",
        "one\ntwo\nthree\nfour\nfive\nsix\nseven",
        "one\ntwo\nthree\n\nfour\nfive\n\u0000\nseven",
    )

    @Test
    fun testSave() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val file = File.createTempFile("storage", null)
        file.deleteOnExit()
        for (value in fileValues) {
            LocalFileStorage(file, ioDispatcher = dispatcher).save(value)
            assertEquals(value, file.readText())
        }
    }

    @Test
    fun testLoad() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val file = File.createTempFile("storage", null)
        file.deleteOnExit()
        for (value in fileValues) {
            file.writeText(value)
            assertEquals(value, LocalFileStorage(file, ioDispatcher = dispatcher).load())
        }
    }

    @Test
    fun testLoadEmpty() = runTest {
        val file = File.createTempFile("storage", null)
        file.delete()
        assertEquals(
            "",
            LocalFileStorage(file, ioDispatcher = StandardTestDispatcher(testScheduler)).load()
        )
    }
}

class DatasetJsonStorageTest {
    private fun exampleDatasets() = sequenceOf(
        Dataset(),
        Dataset().also {
            it.newTask("test")
        }
    )

    @Test
    fun testSave() = runTest {
        val stringStorage = VarStorage("")
        for (dataset in exampleDatasets()) {
            DatasetJsonStorage(stringStorage).save(dataset)
            assertDatasetsEqual(dataset, Json.decodeFromString(stringStorage.value))
        }
    }

    @Test
    fun testLoad() = runTest {
        for (dataset in exampleDatasets()) {
            assertDatasetsEqual(
                dataset,
                DatasetJsonStorage(VarStorage(Json.encodeToString(dataset))).load()
            )
        }
    }

    @Test
    fun testLoadEmpty() = runTest {
        assertDatasetsEqual(
            Dataset(),
            DatasetJsonStorage(VarStorage("")).load()
        )
    }
}
