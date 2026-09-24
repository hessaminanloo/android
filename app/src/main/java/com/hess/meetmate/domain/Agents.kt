package com.hess.meetmate.domain

data class AiAgent(val id: String, val name: String, val role: String, val tint: Long, val enabled: Boolean = true)

object AgentCatalog {
    val all = listOf(
        AiAgent("summary", "Summary", "Clear recap and key points", 0xFF536DFE),
        AiAgent("tasks", "Task extraction", "Owners, deadlines and priorities", 0xFF12A594),
        AiAgent("ceo", "CEO", "Strategy, decisions and risks", 0xFFEF8354),
        AiAgent("technical", "Technical manager", "Architecture and engineering risk", 0xFF7C5CFC),
        AiAgent("critical", "Critical", "Challenge assumptions and gaps", 0xFFE05263),
        AiAgent("translator", "Translator", "Persian ↔ English output", 0xFF2D9CDB)
    )
}

interface AiProvider { suspend fun analyze(transcript: String, agentIds: List<String>): AnalysisResult }
data class AnalysisResult(val summary: String, val tasks: List<String>, val insights: List<String>)

class LocalMeetingProvider : AiProvider {
    override suspend fun analyze(transcript: String, agentIds: List<String>): AnalysisResult {
        val hasContent = transcript.isNotBlank()
        return AnalysisResult(
            if (hasContent) "این جلسه درباره ${transcript.take(70)}… بود." else "برای ساخت خلاصه، چند دقیقه گفتگو ضبط کنید.",
            if (agentIds.contains("tasks") && hasContent) listOf("بازبینی نکات جلسه و تعیین مالک") else emptyList(),
            if (hasContent) listOf("گفتگوی جلسه آماده تحلیل دقیق‌تر است") else emptyList()
        )
    }
}
