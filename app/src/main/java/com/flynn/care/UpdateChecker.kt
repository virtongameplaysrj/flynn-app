package com.flynn.care

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val version: String, val url: String)

/**
 * Consulta a última release pública no GitHub. Quem instala de fato é o Obtainium
 * (ou você, tocando no aviso); isto é só um lembrete de que há versão nova.
 */
object UpdateChecker {
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val repo = BuildConfig.GITHUB_REPO
        if (repo.isBlank()) return@withContext null
        runCatching {
            val c = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                UpdateInfo(j.getString("tag_name").removePrefix("v"), j.getString("html_url"))
            } finally {
                c.disconnect()
            }
        }.getOrNull()?.takeIf { isNewer(it.version, BuildConfig.VERSION_NAME) }
    }

    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.split('.').map { p -> p.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
