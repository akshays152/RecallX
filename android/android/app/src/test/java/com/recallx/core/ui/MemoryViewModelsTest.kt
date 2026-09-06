package com.recallx.core.ui

import com.recallx.core.model.*
import com.recallx.data.repository.RecallXRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun homeExposesLoadedMemories() = runTest {
        val vm = HomeViewModel(FakeRepository(listOf(sampleMemory())))
        advanceUntilIdle()
        assertEquals("Saved receipt", vm.uiState.value.memories.single().title)
        assertEquals(null, vm.uiState.value.error)
    }

    @Test fun homeExposesSafeErrorState() = runTest {
        val vm = HomeViewModel(FakeRepository(failure = IllegalStateException("temporary failure")))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error!!.isNotBlank())
        assertEquals(false, vm.uiState.value.isLoading)
    }
}

private class FakeRepository(private val memories: List<Memory> = emptyList(), private val failure: Throwable? = null) : RecallXRepository {
    override suspend fun getMemories(type: String?, page: Int, pageSize: Int, sort: String): List<Memory> { failure?.let { throw it }; return memories }
    override suspend fun getMemory(id: String) = memories.first()
    override suspend fun getMemoryContent(id: String) = MemoryContent(null, byteArrayOf())
    override suspend fun getMemoryStatus(id: String) = MemoryStatus(id, ProcessingStatus.READY, null, null, false)
    override suspend fun createMemory(file: File, source: String, fileType: String?) = error("unused")
    override suspend fun deleteMemory(id: String) = Unit
    override suspend fun getRelatedMemories(id: String) = emptyList<SearchResult>()
    override suspend fun searchMemories(query: String) = error("unused")
    override suspend fun visualSearch(file: File, question: String?) = error("unused")
}

private fun sampleMemory() = Memory("id", "Saved receipt", "Receipt summary", MemoryFileType.PDF, "2026-09-07T10:15:30Z", "Scanner", ProcessingStatus.READY, null, null, "", emptyMap(), emptyList(), "violet", null, false, null, false, null)
