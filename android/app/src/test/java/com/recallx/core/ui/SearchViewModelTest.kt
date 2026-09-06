package com.recallx.core.ui

import android.content.ContentResolver
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
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun blankAndWhitespaceQueriesAreRejected() = runTest {
        val vm = SearchViewModel(SearchFakeRepository())
        vm.submit("   ")
        assertEquals(SearchPhase.ERROR, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.error!!.isNotBlank())
    }

    @Test fun successfulSearchPreservesBackendOrderAndMetadata() = runTest {
        val first = sampleResult("first", 0.9, "Hotel match")
        val second = sampleResult("second", 0.7, null)
        val vm = SearchViewModel(SearchFakeRepository(response = SearchResponse("hotel", listOf(first, second), "demo")))
        vm.submit(" hotel "); advanceUntilIdle()
        assertEquals(SearchPhase.SUCCESS, vm.uiState.value.phase)
        assertEquals(listOf("first", "second"), vm.uiState.value.results.map { it.memory.id })
        assertEquals("Hotel match", vm.uiState.value.results.first().matchReason)
        assertEquals(0.9, vm.uiState.value.results.first().score, 0.0)
    }

    @Test fun emptyAndFailedSearchesExposeExplicitStates() = runTest {
        val emptyVm = SearchViewModel(SearchFakeRepository(response = SearchResponse("nothing", emptyList(), "demo")))
        emptyVm.submit("nothing"); advanceUntilIdle(); assertEquals(SearchPhase.EMPTY, emptyVm.uiState.value.phase)
        val errorVm = SearchViewModel(SearchFakeRepository(failure = IllegalStateException("internal")))
        errorVm.submit("hotel"); advanceUntilIdle(); assertEquals(SearchPhase.ERROR, errorVm.uiState.value.phase)
    }

    @Test fun duplicateSubmitDoesNotStartTwoRequests() = runTest {
        val repo = SearchFakeRepository(response = SearchResponse("hotel", listOf(sampleResult("one", null, null)), "demo"))
        val vm = SearchViewModel(repo)
        vm.submit("hotel"); vm.submit("hotel"); advanceUntilIdle()
        assertEquals(1, repo.calls)
    }
}

private class SearchFakeRepository(private val response: SearchResponse? = null, private val failure: Throwable? = null) : RecallXRepository {
    var calls = 0
    override suspend fun getMemories(type: String?, page: Int, pageSize: Int, sort: String) = emptyList<Memory>()
    override suspend fun getMemory(id: String) = sampleResult(id, null, null).memory
    override suspend fun getMemoryContent(id: String) = MemoryContent(null, byteArrayOf())
    override suspend fun getMemoryStatus(id: String) = MemoryStatus(id, ProcessingStatus.READY, null, null, false)
    override suspend fun createMemory(file: File, source: String, fileType: String?) = error("unused")
    override suspend fun deleteMemory(id: String) = Unit
    override suspend fun getRelatedMemories(id: String) = emptyList<SearchResult>()
    override suspend fun uploadMemory(file: SelectedMemoryFile, source: String, contentResolver: ContentResolver) = error("unused")
    override suspend fun searchMemories(query: String): SearchResponse { calls++; failure?.let { throw it }; return response ?: error("unused") }
    override suspend fun visualSearch(file: File, question: String?) = error("unused")
}

private fun sampleResult(id: String, score: Double?, reason: String?) = SearchResult(Memory(id, id, "Summary", MemoryFileType.SCREENSHOT, "2026-09-07T10:15:30Z", "Screenshot", ProcessingStatus.READY, null, null, "", emptyMap(), emptyList(), "violet", null, false, null, false, null), score, reason, "demo")
