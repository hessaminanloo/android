package com.hess.meetmate.integration

import com.hess.meetmate.data.MeetingEntity
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Small provider boundary for personal bot delivery. Credentials are supplied by Settings in a later storage layer. */
class TelegramClient {
    fun sendMeeting(botToken: String, chatId: String, meeting: MeetingEntity): Result<Unit> = runCatching {
        val body = buildString {
            append("<b>${meeting.title.escapeHtml()}</b>\n")
            append("${meeting.participants.escapeHtml()}\n\n")
            append("<b>Summary</b>\n${meeting.summary.escapeHtml()}\n\n")
            append("<b>Tasks</b>\n${meeting.tasks.escapeHtml()}")
        }
        val encoded = URLEncoder.encode(body, Charsets.UTF_8.name())
        val connection = URL("https://api.telegram.org/bot$botToken/sendMessage?chat_id=$chatId&parse_mode=HTML&text=$encoded").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        check(connection.responseCode in 200..299) { "Telegram returned ${connection.responseCode}" }
        connection.disconnect()
    }
    private fun String.escapeHtml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
