package com.flynn.care.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

enum class ItemType(val key: String, val tab: String, val singular: String, val emoji: String) {
    VACCINE("vaccine", "Vacinas", "vacina", "💉"),
    FLEA("flea", "Antipulgas", "antipulgas / vermífugo", "🛡️"),
    EXAM("exam", "Exames", "exame", "🩺");

    companion object {
        fun from(key: String?): ItemType = entries.firstOrNull { it.key == key } ?: VACCINE
    }
}

/** Vacinas e antipulgas: 2 dias antes, 1 dia antes e no dia. */
val FIXED_OFFSETS = listOf(2, 1, 0)
const val DEFAULT_HOUR = 9

/** Uma vacina, aplicação de antipulgas/vermífugo ou exame. */
data class CareItem(
    val id: String = "",
    val type: ItemType = ItemType.VACCINE,
    val title: String = "",
    val dose: String = "",
    val date: String = "",          // yyyy-MM-dd
    val notes: String = "",
    val offsets: List<Int> = FIXED_OFFSETS, // dias antes da data em que lembrar
    val hour: Int = DEFAULT_HOUR,   // hora do lembrete
    val done: Boolean = false,
) {
    fun toMap(): Map<String, Any> = mapOf(
        "type" to type.key, "title" to title, "dose" to dose, "date" to date,
        "notes" to notes, "offsets" to offsets, "hour" to hour, "done" to done,
    )

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("type", type.key).put("title", title).put("dose", dose)
        .put("date", date).put("notes", notes).put("offsets", JSONArray(offsets))
        .put("hour", hour).put("done", done)

    companion object {
        fun fromMap(id: String, m: Map<String, Any?>) = CareItem(
            id = id,
            type = ItemType.from(m["type"] as? String),
            title = m["title"] as? String ?: "",
            dose = m["dose"] as? String ?: "",
            date = m["date"] as? String ?: "",
            notes = m["notes"] as? String ?: "",
            offsets = (m["offsets"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: FIXED_OFFSETS,
            hour = (m["hour"] as? Number)?.toInt() ?: DEFAULT_HOUR,
            done = m["done"] as? Boolean ?: false,
        )

        fun fromJson(o: JSONObject) = CareItem(
            id = o.optString("id"),
            type = ItemType.from(o.optString("type")),
            title = o.optString("title"),
            dose = o.optString("dose"),
            date = o.optString("date"),
            notes = o.optString("notes"),
            offsets = o.optJSONArray("offsets")?.let { a -> (0 until a.length()).map { a.getInt(it) } }
                ?: FIXED_OFFSETS,
            hour = o.optInt("hour", DEFAULT_HOUR),
            done = o.optBoolean("done"),
        )

        fun listToJson(items: List<CareItem>): String = JSONArray(items.map { it.toJson() }).toString()

        fun listFromJson(s: String?): List<CareItem> =
            if (s.isNullOrBlank()) emptyList()
            else runCatching {
                val a = JSONArray(s)
                (0 until a.length()).map { fromJson(a.getJSONObject(it)) }
            }.getOrDefault(emptyList())
    }
}

/** Um clique no botão "Dei comida". */
data class Feeding(val id: String, val at: Instant, val by: String)
