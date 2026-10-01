package com.recallx.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.recallx.core.network.dto.MemoryDto
import com.recallx.core.network.dto.SearchHitDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

/** Bundled demo data only. Live ingestion and ranking remain in the existing engine. */
class SampleRecallXRepository(context: Context) : RecallXRepository {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("recallx_samples", Context.MODE_PRIVATE)
    private val samples = app.assets.open("demo/memories.json").bufferedReader().use {
        Json.decodeFromString<List<MemoryDto>>(it.readText())
    }
    override val baseUrl = "asset://demo/"

    override suspend fun listMemories(): List<MemoryDto> {
        val hidden = preferences.getStringSet("hidden", emptySet()).orEmpty()
        return samples.filterNot { it.id in hidden }
    }

    override suspend fun search(query: String): List<SearchHitDto> = SampleSearch.rank(query, listMemories())

    override suspend fun deleteMemory(id: String) {
        require(samples.any { it.id == id })
        val hidden = preferences.getStringSet("hidden", emptySet()).orEmpty().toMutableSet()
        hidden.add(id)
        preferences.edit().putStringSet("hidden", hidden).apply()
    }

    fun restore() { preferences.edit().remove("hidden").apply() }

    override fun contentUrl(memory: MemoryDto): String? = samples.find { it.id == memory.id }?.contentUrl

    suspend fun originalUri(memory: MemoryDto): Uri = withContext(Dispatchers.IO) {
        val known = samples.first { it.id == memory.id }
        val asset = requireNotNull(known.contentUrl).removePrefix("asset://")
        val directory = File(app.cacheDir, "sample-originals").apply { mkdirs() }
        val target = File(directory, asset.substringAfterLast('/'))
        app.assets.open(asset).use { input -> target.outputStream().use { input.copyTo(it) } }
        FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", target)
    }

    override suspend fun ingestFile(file: File, originalName: String, sourceUri: String, mediaType: String): MemoryDto =
        error("Connect the Recall Engine to add your own memories.")
    override suspend fun ingestText(text: String, title: String): MemoryDto =
        error("Connect the Recall Engine to add your own memories.")
}

/** Transparent keyword search over the bundled extracted content, not simulated AI. */
internal object SampleSearch {
    private val ignored = setOf("a", "an", "the", "that", "this", "where", "i", "my", "me", "find", "saved", "show", "get", "was", "of", "for", "with")
    private val concepts = mapOf(
        "price" to setOf("price", "cost", "amount", "total", "fare", "rate", "paid"),
        "hotel" to setOf("hotel", "stay", "room", "booking"),
        "flight" to setOf("flight", "ticket", "boarding", "pnr"),
        "food" to setOf("food", "menu", "restaurant"),
        "meeting" to setOf("meeting", "review", "deadline"),
        "receipt" to setOf("receipt", "invoice", "bill", "paid"),
    )
    private fun tokens(text: String): Set<String> = Regex("[\\p{L}\\p{N}]+").findAll(text.lowercase(Locale.ROOT))
        .map { it.value }.filter { it.length > 1 && it !in ignored }.toSet()

    fun rank(query: String, memories: List<MemoryDto>): List<SearchHitDto> {
        val terms = tokens(query)
        if (terms.isEmpty()) return emptyList()
        val expanded = terms + concepts.filterKeys { it in terms }.values.flatten()
        return memories.mapNotNull { memory ->
            val source = "${memory.title} ${memory.text} ${memory.labels.joinToString(" ")}"
            val sourceTokens = tokens(source)
            val matched = terms.intersect(sourceTokens)
            val related = expanded.intersect(sourceTokens)
            if (related.isEmpty()) return@mapNotNull null
            val titleMatches = tokens(memory.title).intersect(terms).size
            val exact = source.contains(query.trim(), ignoreCase = true)
            val score = (0.75 * matched.size / terms.size + 0.15 * related.size / expanded.size +
                0.08 * titleMatches / terms.size + if (exact) 0.02 else 0.0).coerceAtMost(1.0)
            val lines = memory.text.lineSequence().filter { line -> tokens(line).intersect(expanded).isNotEmpty() }.take(2).toList()
            SearchHitDto(memory, score, listOf("Sample text match: ${(matched.ifEmpty { related }).sorted().take(6).joinToString() }"), lines)
        }.sortedByDescending { it.score }
    }
}
