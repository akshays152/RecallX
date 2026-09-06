package com.recallx.core.ui

import android.content.ContentResolver
import android.net.Uri
import com.recallx.core.camera.PreparedSearchImage
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
class VisualSearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun startsIdleWithDefaultQuestion() {
        val vm = VisualSearchViewModel(VisualFakeRepository())
        assertEquals(VisualSearchPhase.IDLE, vm.uiState.value.phase)
        assertEquals("Have I seen this before?", vm.uiState.value.question)
    }

    @Test fun successfulSearchExposesBackendResults() = runTest {
        val file = File.createTempFile("visual-test-", ".jpg")
        val vm = VisualSearchViewModel(VisualFakeRepository(result = VisualSearchResponse("q1", "Find this", listOf(sampleResult()), "demo")))
        vm.setImage(PreparedSearchImage(file)); vm.setQuestion("Find this"); vm.search(); advanceUntilIdle()
        assertEquals(VisualSearchPhase.SUCCESS, vm.uiState.value.phase)
        assertEquals("Saved hotel", vm.uiState.value.results.single().memory.title)
        file.delete()
    }

    @Test fun emptySearchExposesEmptyState() = runTest {
        val file = File.createTempFile("visual-test-", ".jpg")
        val vm = VisualSearchViewModel(VisualFakeRepository(result = VisualSearchResponse("q1", null, emptyList(), "demo")))
        vm.setImage(PreparedSearchImage(file)); vm.search(); advanceUntilIdle()
        assertEquals(VisualSearchPhase.EMPTY, vm.uiState.value.phase); file.delete()
    }

    @Test fun failedSearchExposesUserSafeError() = runTest {
        val file = File.createTempFile("visual-test-", ".jpg")
        val vm = VisualSearchViewModel(VisualFakeRepository(failure = IllegalStateException("server internals")))
        vm.setImage(PreparedSearchImage(file)); vm.search(); advanceUntilIdle()
        assertEquals(VisualSearchPhase.ERROR, vm.uiState.value.phase); assertTrue(vm.uiState.value.error!!.isNotBlank()); file.delete()
    }
}

private class VisualFakeRepository(private val result: VisualSearchResponse? = null, private val failure: Throwable? = null) : RecallXRepository {
    override suspend fun getMemories(type: String?, page: Int, pageSize: Int, sort: String) = emptyList<Memory>()
    override suspend fun getMemory(id: String) = sampleResult().memory
    override suspend fun getMemoryContent(id: String) = MemoryContent(null, byteArrayOf())
    override suspend fun getMemoryStatus(id: String) = MemoryStatus(id, ProcessingStatus.READY, null, null, false)
    override suspend fun createMemory(file: File, source: String, fileType: String?) = error("unused")
    override suspend fun deleteMemory(id: String) = Unit
    override suspend fun getRelatedMemories(id: String) = emptyList<SearchResult>()
    override suspend fun uploadMemory(file: SelectedMemoryFile, source: String, contentResolver: ContentResolver) = error("unused")
    override suspend fun searchMemories(query: String) = error("unused")
    override suspend fun visualSearch(file: File, question: String?): VisualSearchResponse { failure?.let { throw it }; return result ?: error("unused") }
}

private fun sampleResult() = SearchResult(Memory("memory-1", "Saved hotel", "Hotel booking", MemoryFileType.SCREENSHOT, "2026-09-07T10:15:30Z", "Screenshot", ProcessingStatus.READY, null, null, "", emptyMap(), emptyList(), "violet", null, false, null, false, null), 0.91, "Visual match", "demo")
