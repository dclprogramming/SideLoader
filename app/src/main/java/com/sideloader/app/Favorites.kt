package com.sideloader.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Fav(val name: String, val url: String)

/** Persists saved URLs as JSON in SharedPreferences. */
class Favorites(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    fun all(): List<Fav> {
        val arr = JSONArray(prefs.getString("list", "[]"))
        return List(arr.length()) { val o = arr.getJSONObject(it); Fav(o.getString("name"), o.getString("url")) }
    }

    private fun save(list: List<Fav>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("url", it.url)) }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    fun add(f: Fav) { addAll(listOf(f)) }
    fun remove(f: Fav) = save(all().filter { it.url != f.url })

    /** Adds new entries, skipping URLs already saved. Returns how many were added. */
    fun addAll(items: List<Fav>): Int {
        val cur = all()
        val known = cur.map { it.url }.toSet()
        val fresh = items.distinctBy { it.url }.filter { it.url !in known }
        if (fresh.isNotEmpty()) save(cur + fresh)
        return fresh.size
    }

    companion object {
        /**
         * One favorite per line: "Name | URL" or just "URL".
         * Blank lines and lines starting with # are ignored.
         */
        fun parse(text: String): List<Fav> = text.removePrefix("\uFEFF").lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val name = if ("|" in line) line.substringBefore("|").trim() else ""
                val raw = if ("|" in line) line.substringAfter("|").trim() else line
                if (raw.isEmpty() || " " in raw || "." !in raw) return@mapNotNull null
                val url = if (Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(raw)) raw else "https://$raw"
                Fav(name.ifBlank { url }, url)
            }.toList()
    }
}
