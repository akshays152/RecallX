package com.recallx.data.repository

import com.recallx.core.network.dto.MemoryDto
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SampleSearchTest {
    private val memories: List<MemoryDto> = javaClass.classLoader!!.getResourceAsStream("demo/memories.json")!!.bufferedReader().use {
        Json.decodeFromString(it.readText())
    }

    @Test fun bundleContainsAllNineOriginalsAndPreviews() {
        assertEquals(9, memories.size)
        assertEquals(9, memories.map { it.id }.toSet().size)
        assertEquals(6, memories.count { it.mediaType.startsWith("image/") })
        assertEquals(3, memories.count { it.mediaType == "application/pdf" })
        memories.forEach { memory ->
            assertTrue(memory.text.isNotBlank())
            listOf(memory.contentUrl, memory.thumbnailUrl).forEach { asset ->
                assertNotNull(asset)
                javaClass.classLoader!!.getResourceAsStream(asset!!.removePrefix("asset://")).use { stream -> assertNotNull(stream) }
            }
        }
    }

    @Test fun searchesRealExtractedPhotoAndDocumentContent() {
        val cases = mapOf(
            "Find that screenshot where I saved the hotel price" to "sample-hotel_booking",
            "flight ticket PNR" to "sample-flight_ticket",
            "laptop stand invoice amount paid" to "sample-equipment_invoice",
            "Goa hotel check-in breakfast" to "sample-goa_travel_itinerary",
            "Riya PDF ingestion deadline" to "sample-design_review_notes",
        )
        cases.forEach { (query, id) -> assertEquals(query, id, SampleSearch.rank(query, memories).first().memory.id) }
    }

    @Test fun noMatchesOrBlankQueryDoesNotReturnFabricatedResults() {
        assertTrue(SampleSearch.rank("", memories).isEmpty())
        assertTrue(SampleSearch.rank("quasarxyz nebula987", memories).isEmpty())
    }

    @Test fun searchIsCaseInsensitiveAndHonorsHiddenSamples() {
        assertEquals(SampleSearch.rank("hotel price", memories).map { it.memory.id },
            SampleSearch.rank("HOTEL PRICE", memories).map { it.memory.id })
        val visible = memories.filterNot { it.id == "sample-hotel_booking" }
        assertFalse(SampleSearch.rank("hotel price", visible).any { it.memory.id == "sample-hotel_booking" })
    }
}
