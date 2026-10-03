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
        private val urlRx = Regex("^(https?://\\S+|[\\w-]+(\\.[\\w-]+)+(/\\S*)?)$", RegexOption.IGNORE_CASE)

        /**
         * One favorite per line. The URL can sit anywhere on the line and any other
         * words become the title, e.g. "http://someplace.com JoesGarage",
         * "Joes Garage | someplace.com" or just "someplace.com".
         * Blank lines and lines starting with # are ignored.
         */
        fun parse(text: String): List<Fav> = text.removePrefix("\uFEFF").lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val tokens = line.split(Regex("[\\s\\u00A0|]+")).filter { it.isNotEmpty() }
                var idx = tokens.indexOfFirst { it.startsWith("http", true) && urlRx.matches(it) }
                if (idx < 0) idx = tokens.indexOfFirst { urlRx.matches(it) }
                if (idx < 0) return@mapNotNull null
                val raw = tokens[idx]
                val url = if (raw.startsWith("http", true)) raw else "https://$raw"
                val name = (tokens.take(idx) + tokens.drop(idx + 1)).joinToString(" ")
                    .trim(' ', '-', ',', ':', '\u2013', '\u2014')
                Fav(name.ifBlank { url }, url)
            }.toList()
    }
}
