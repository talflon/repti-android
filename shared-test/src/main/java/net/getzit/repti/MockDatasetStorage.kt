package net.getzit.repti

class MockDatasetStorage(initial: Dataset = Dataset()) : Storage<Dataset> {
    private var dataset = initial.copy()

    override suspend fun load(): Dataset = dataset.copy()

    override suspend fun save(value: Dataset) {
        dataset = value.copy()
    }
}