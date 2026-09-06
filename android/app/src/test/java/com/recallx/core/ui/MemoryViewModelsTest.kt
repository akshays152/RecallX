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
import android.content.ContentResolver

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

    @Test fun uploadMovesFromProcessingToSuccessWhenBackendBecomesReady() = runTest {
        val processing = sampleMemory().copy(processingStatus = ProcessingStatus.PROCESSING)
        val ready = processing.copy(processingStatus = ProcessingStatus.READY)
        val vm = AddMemoryViewModel(FakeRepository(uploadResult = IngestionResult(processing, "demo", "stored"), detailMemory = ready, statuses = listOf(MemoryStatus("id", ProcessingStatus.PROCESSING, null, null, false), MemoryStatus("id", ProcessingStatus.READY, null, null, true))))
        vm.uploadSelectedFile(SelectedMemoryFile(android.net.Uri.parse("content://test/file"), "file.pdf", "application/pdf", 10L, "pdf"), ContentResolver(null))
        advanceUntilIdle()
        assertEquals(AddMemoryPhase.SUCCESS, vm.uiState.value.phase)
        assertEquals("id", vm.uiState.value.memory?.id)
    }

    @Test fun uploadShowsProcessingFailure() = runTest {
        val processing = sampleMemory().copy(processingStatus = ProcessingStatus.PROCESSING)
        val vm = AddMemoryViewModel(FakeRepository(uploadResult = IngestionResult(processing, "demo", "stored"), statuses = listOf(MemoryStatus("id", ProcessingStatus.FAILED, null, "Processing failed.", false))))
        vm.uploadSelectedFile(SelectedMemoryFile(android.net.Uri.parse("content://test/file"), "file.pdf", "application/pdf", 10L, "pdf"), ContentResolver(null))
        advanceUntilIdle()
        assertEquals(AddMemoryPhase.ERROR, vm.uiState.value.phase)
        assertEquals("Processing failed.", vm.uiState.value.error)
    }
}

private class FakeRepository(private val memories: List<Memory> = emptyList(), private val failure: Throwable? = null, private val uploadResult: IngestionResult? = null, private val detailMemory: Memory? = null, statuses: List<MemoryStatus> = emptyList()) : RecallXRepository {
    private val statuses = statuses.toMutableList()
    override suspend fun getMemories(type: String?, page: Int, pageSize: Int, sort: String): List<Memory> { failure?.let { throw it }; return memories }
    override suspend fun getMemory(id: String) = detailMemory ?: memories.first()
    override suspend fun getMemoryContent(id: String) = MemoryContent(null, byteArrayOf())
    override suspend fun getMemoryStatus(id: String) = statuses.removeFirstOrNull() ?: MemoryStatus(id, ProcessingStatus.READY, null, null, false)
    override suspend fun createMemory(file: File, source: String, fileType: String?) = error("unused")
    override suspend fun deleteMemory(id: String) = Unit
    override suspend fun getRelatedMemories(id: String) = emptyList<SearchResult>()
    override suspend fun uploadMemory(file: SelectedMemoryFile, source: String, contentResolver: ContentResolver): IngestionResult { failure?.let { throw it }; return uploadResult ?: error("unused") }
    override suspend fun searchMemories(query: String) = error("unused")
    override suspend fun visualSearch(file: File, question: String?) = error("unused")
}

private fun sampleMemory() = Memory("id", "Saved receipt", "Receipt summary", MemoryFileType.PDF, "2026-09-07T10:15:30Z", "Scanner", ProcessingStatus.READY, null, null, "", emptyMap(), emptyList(), "violet", null, false, null, false, null)
