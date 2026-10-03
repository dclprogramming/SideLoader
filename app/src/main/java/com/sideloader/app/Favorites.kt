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

    fun add(f: Fav) { val l = all(); if (l.none { it.url == f.url }) save(l + f) }
    fun remove(f: Fav) = save(all().filter { it.url != f.url })
}
