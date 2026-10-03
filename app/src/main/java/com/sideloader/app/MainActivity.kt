package com.sideloader.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import android.widget.LinearLayout.LayoutParams.MATCH_PARENT

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var input: EditText
    private lateinit var bar: ProgressBar
    private lateinit var status: TextView
    private lateinit var favs: Favorites

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        favs = Favorites(this)
        input = EditText(this).apply {
            hint = "Enter URL or search"; setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, _, _ -> go(text.toString()); true }
        }
        val row = LinearLayout(this).apply {
            fun btn(label: String, action: () -> Unit) = addView(
                Button(this@MainActivity).apply { text = label; setOnClickListener { action() } },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            btn("Go") { go(input.text.toString()) }
            btn("★ Save") { saveFavorite() }
            btn("Favorites") { showFavorites() }
        }
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        status = TextView(this)
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(v: WebView, url: String) { input.setText(url) }
            }
            setDownloadListener { url, _, disp, _, _ -> fetch(url, disp) }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(input); addView(row); addView(bar); addView(status)
            addView(web, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        })
    }

    private fun go(text: String) {
        val t = text.trim()
        when {
            t.isEmpty() -> return
            Regex("^https?://.*", RegexOption.IGNORE_CASE).matches(t) ->
                if (t.substringBefore('?').endsWith(".apk", true)) fetch(t, null) else web.loadUrl(t)
            "." in t && " " !in t -> go("https://$t")
            else -> web.loadUrl("https://www.google.com/search?q=" + java.net.URLEncoder.encode(t, "UTF-8"))
        }
    }

    // ---- Favorites ----
    private fun saveFavorite() {
        val url = input.text.toString().trim().ifEmpty { web.url ?: "" }
        if (url.isEmpty()) { toast("Nothing to save"); return }
        val name = EditText(this).apply { setText(web.title?.takeIf { url == web.url } ?: url); setSingleLine() }
        AlertDialog.Builder(this).setTitle("Save favorite").setView(name)
            .setPositiveButton("Save") { _, _ ->
                favs.add(Fav(name.text.toString().ifBlank { url }, url)); toast("Saved")
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showFavorites() {
        val list = favs.all()
        if (list.isEmpty()) { toast("No favorites yet. Enter a URL and tap ★ Save."); return }
        val dlg = AlertDialog.Builder(this).setTitle("Favorites (long-press to delete)")
            .setItems(list.map { "${it.name}\n${it.url}" }.toTypedArray()) { _, i -> go(list[i].url) }
            .create()
        dlg.show()
        dlg.listView.setOnItemLongClickListener { _, _, i, _ ->
            AlertDialog.Builder(this).setMessage("Delete \"${list[i].name}\"?")
                .setPositiveButton("Delete") { _, _ -> favs.remove(list[i]); dlg.dismiss(); showFavorites() }
                .setNegativeButton("Cancel", null).show()
            true
        }
    }

    // ---- Downloads ----
    private fun fetch(url: String, disposition: String?) {
        status.text = "Downloading…"; bar.progress = 0
        Downloads.start(this, url, disposition,
            { p -> runOnUiThread { bar.progress = p } },
            { r -> runOnUiThread {
                r.onSuccess { f ->
                    status.text = "Saved ${f.name}"
                    if (f.extension.equals("apk", true)) Downloads.install(this, f)
                }.onFailure { status.text = "Failed: ${it.message}" }
            } })
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (web.canGoBack()) web.goBack() else super.onBackPressed() }
}
