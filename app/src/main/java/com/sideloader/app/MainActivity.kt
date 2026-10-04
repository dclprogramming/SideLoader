package com.sideloader.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import android.os.Environment
import android.text.format.DateUtils
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    private val NAVY = 0xFF0F4C81.toInt()
    private val AQUA = 0xFF4FD1C5.toInt()
    private val TINT = 0xFFEAF4FD.toInt()
    private val MUTED = 0xFF5B7FA3.toInt()
    private val BG = 0xFF062036.toInt()
    private var pending: File? = null
    private val HOME = """<html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
        <body style="margin:0;height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;background:#062036;color:#EAF4FD;font-family:sans-serif;text-align:center">
        <div style="font-size:30px;font-weight:bold;color:#4FD1C5">SideLoader</div>
        <p style="opacity:.8;padding:0 24px">Enter a URL above, or open Favorites.</p></body></html>"""

    private lateinit var web: WebView
    private lateinit var input: EditText
    private lateinit var bar: ProgressBar
    private lateinit var status: TextView
    private lateinit var favs: Favorites
    private val pickFile = 1
    private val saveFile = 2

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0, wt: Float = 0f) =
        LinearLayout.LayoutParams(w, h, wt).apply { setMargins(dp(l), dp(t), dp(r), dp(b)) }
    private fun shape(fill: Int, radius: Int, stroke: Int = 0, strokeW: Int = 0) = GradientDrawable().apply {
        setColor(fill); cornerRadius = radius.toFloat(); if (strokeW > 0) setStroke(strokeW, stroke)
    }
    private fun focusable(normal: Drawable, focused: Drawable) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), focused)
        addState(intArrayOf(android.R.attr.state_pressed), focused)
        addState(intArrayOf(), normal)
    }
    private fun pill(label: String, fill: Int, text: Int, action: () -> Unit) = Button(this).apply {
        this.text = label; isAllCaps = false; setTextColor(text); textSize = 13f
        typeface = Typeface.DEFAULT_BOLD; stateListAnimator = null; minHeight = 0; minimumHeight = 0
        setPadding(dp(4), dp(10), dp(4), dp(10))
        val ring = if (fill == AQUA) NAVY else AQUA
        background = focusable(shape(fill, dp(22)), shape(fill, dp(22), ring, dp(3)))
        setOnClickListener { action() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        favs = Favorites(this)

        input = EditText(this).apply {
            hint = "Enter URL or search"; setSingleLine(); textSize = 15f
            setTextColor(NAVY); setHintTextColor(MUTED)
            background = null
            setPadding(dp(18), dp(10), dp(46), dp(10))
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, _, _ -> go(text.toString()); true }
        }
        val clear = TextView(this).apply {
            text = "✕"; textSize = 16f; setTextColor(MUTED); gravity = Gravity.CENTER
            isFocusable = true; isClickable = true; visibility = View.GONE
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = focusable(shape(Color.TRANSPARENT, dp(18)), shape(TINT, dp(18), AQUA, dp(2)))
            setOnClickListener { input.setText(""); input.requestFocus() }
        }
        input.doAfterTextChanged { clear.visibility = if (it.isNullOrEmpty()) View.GONE else View.VISIBLE }
        val box = FrameLayout(this).apply {
            background = shape(Color.WHITE, dp(24))
            addView(input, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(clear, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(6) })
        }
        val titleRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(this@MainActivity).apply { setImageResource(R.drawable.ic_launcher) }, lp(dp(40), dp(40), r = 12))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = "SideLoader"; textSize = 22f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD })
                addView(TextView(this@MainActivity).apply {
                    text = "Download & install apps"; textSize = 12f; setTextColor(AQUA) })
            })
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val r = dp(26).toFloat()
            background = GradientDrawable().apply { setColor(NAVY); cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r) }
            setPadding(dp(18), dp(16), dp(18), dp(20))
            addView(titleRow)
            addView(box, lp(MATCH_PARENT, WRAP_CONTENT, t = 14))
        }
        val row = LinearLayout(this).apply {
            setPadding(dp(12), dp(12), dp(12), dp(4))
            fun add(b: Button) = addView(b, lp(0, WRAP_CONTENT, l = 4, r = 4, wt = 1f))
            add(pill("Go", AQUA, NAVY) { go(input.text.toString()) })
            add(pill("★ Save", NAVY, Color.WHITE) { saveFavorite() })
            add(pill("Favorites", NAVY, Color.WHITE) { showFavorites() })
            add(pill("Downloads", NAVY, Color.WHITE) { showDownloads() })
        }
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            progressTintList = ColorStateList.valueOf(AQUA)
            progressBackgroundTintList = ColorStateList.valueOf(0xFF12395A.toInt())
        }
        status = TextView(this).apply { text = "Ready"; textSize = 13f; setTextColor(TINT) }
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(v: WebView, url: String) { if (url.startsWith("http")) input.setText(url) }
            }
            setDownloadListener { url, _, disp, _, _ -> fetch(url, disp) }
            setBackgroundColor(BG)
            loadDataWithBaseURL(null, HOME, "text/html", "utf-8", null)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            addView(header, lp(MATCH_PARENT, WRAP_CONTENT))
            addView(row, lp(MATCH_PARENT, WRAP_CONTENT))
            addView(bar, lp(MATCH_PARENT, dp(6), l = 16, t = 6, r = 16))
            addView(status, lp(MATCH_PARENT, WRAP_CONTENT, l = 16, t = 4, r = 16, b = 6))
            addView(web, lp(MATCH_PARENT, 0, wt = 1f))
        })
        askInstallPermission()
    }

    // ---- Install permission ----
    private fun canInstall() = Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()

    private fun openInstallSettings() = startActivity(
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))

    private fun askInstallPermission() {
        if (canInstall()) return
        AlertDialog.Builder(this).setTitle("Allow app installs")
            .setMessage("SideLoader needs permission to install apps. Tap Allow, then switch on \"Allow from this source\" and come back.")
            .setPositiveButton("Allow") { _, _ -> openInstallSettings() }
            .setNegativeButton("Later", null).show()
    }

    override fun onResume() {
        super.onResume()
        pending?.let { if (canInstall()) { pending = null; Downloads.install(this, it) } }
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
        val name = EditText(this).apply { setText(web.title?.takeIf { url == web.url } ?: url); setSingleLine(); setPadding(dp(24), dp(12), dp(24), dp(12)) }
        AlertDialog.Builder(this).setTitle("Save favorite").setView(name)
            .setPositiveButton("Save") { _, _ ->
                favs.add(Fav(name.text.toString().ifBlank { url }, url)); toast("Saved")
            }.setNegativeButton("Cancel", null).show()
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
    private fun empty(msg: String) = TextView(this).apply { text = msg; textSize = 14f; setTextColor(MUTED); gravity = Gravity.CENTER; setPadding(0, dp(24), 0, dp(24)) }
    private fun confirm(msg: String, yes: () -> Unit) = AlertDialog.Builder(this).setMessage(msg)
        .setPositiveButton("Delete") { _, _ -> yes() }.setNegativeButton("Cancel", null).show()

    /** A rounded list card: tap to open, ✕ to delete. */
    private fun card(title: String, sub: String, onOpen: () -> Unit, onDelete: () -> Unit) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
        setPadding(dp(16), dp(12), dp(8), dp(12))
        background = focusable(shape(TINT, dp(16)), shape(0xFFD3EBFA.toInt(), dp(16), AQUA, dp(3)))
        setOnClickListener { onOpen() }
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = title; textSize = 16f; setTextColor(NAVY); typeface = Typeface.DEFAULT_BOLD
                setSingleLine(); ellipsize = TextUtils.TruncateAt.END })
            addView(TextView(this@MainActivity).apply {
                text = sub; textSize = 12f; setTextColor(MUTED)
                setSingleLine(); ellipsize = TextUtils.TruncateAt.END })
        }, lp(0, WRAP_CONTENT, wt = 1f))
        addView(TextView(this@MainActivity).apply {
            text = "✕"; textSize = 18f; setTextColor(MUTED); gravity = Gravity.CENTER
            isFocusable = true; isClickable = true; setPadding(dp(14), dp(8), dp(14), dp(8))
            background = focusable(shape(Color.TRANSPARENT, dp(20)), shape(Color.WHITE, dp(20), AQUA, dp(2)))
            setOnClickListener { onDelete() }
        })
    }

    private fun showFavorites() {
        val list = favs.all()
        lateinit var dlg: AlertDialog
        val col = column()
        if (list.isEmpty()) col.addView(empty("No favorites yet.\nTap ★ Save, or use Import below."))
        list.forEach { f ->
            col.addView(card(f.name, f.url,
                { dlg.dismiss(); go(f.url) },
                { confirm("Delete \"${f.name}\"?") { favs.remove(f); dlg.dismiss(); showFavorites() } }),
                lp(MATCH_PARENT, WRAP_CONTENT, b = 12))
        }
        dlg = AlertDialog.Builder(this).setTitle("★ Favorites")
            .setView(ScrollView(this).apply { addView(col) })
            .setNeutralButton("Close", null)
            .setNegativeButton("Import") { _, _ -> showImport() }
            .setPositiveButton("Export") { _, _ -> showExport() }.show()
    }

    // ---- Downloads ----
    private fun showDownloads() {
        val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
        val files = dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
        lateinit var dlg: AlertDialog
        val col = column()
        if (files.isEmpty()) col.addView(empty("No downloads yet."))
        files.forEach { f ->
            val sub = "${Formatter.formatShortFileSize(this, f.length())} · ${DateUtils.getRelativeTimeSpanString(f.lastModified())}"
            col.addView(card(f.name, sub,
                { dlg.dismiss(); openFile(f) },
                { confirm("Delete ${f.name}?") { f.delete(); dlg.dismiss(); showDownloads() } }),
                lp(MATCH_PARENT, WRAP_CONTENT, b = 12))
        }
        val b = AlertDialog.Builder(this).setTitle("Downloads")
            .setView(ScrollView(this).apply { addView(col) })
            .setNegativeButton("Close", null)
        if (files.isNotEmpty()) b.setNeutralButton("Delete all") { _, _ ->
            confirm("Delete all ${files.size} files?") { files.forEach { it.delete() }; toast("Downloads cleared") }
        }
        dlg = b.show()
    }

    private fun openFile(f: File) {
        if (f.extension.equals("apk", true)) { installApk(f); return }
        val uri = FileProvider.getUriForFile(this, "$packageName.provider", f)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
        try {
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: ActivityNotFoundException) { toast("No app can open this file") }
    }

    private fun installApk(f: File) {
        if (canInstall()) Downloads.install(this, f)
        else { pending = f; status.text = "Allow installs, then return to SideLoader"; openInstallSettings() }
    }

    // ---- Import (text file or URL) ----
    private fun showImport() {
        AlertDialog.Builder(this).setTitle("Import favorites")
            .setItems(arrayOf("From a text file", "From a URL")) { _, i ->
                if (i == 0) pickTextFile() else askImportUrl()
            }.show()
    }

    private fun pickTextFile() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("text/*"), pickFile)
        } catch (e: ActivityNotFoundException) {
            toast("No file picker on this device. Use \"From a URL\" instead.")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        if (requestCode == saveFile) {
            runCatching { contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(favoritesText()) } }
                .onSuccess { toast("Exported ${favs.all().size} favorites") }
                .onFailure { toast("Export failed: ${it.message}") }
        } else if (requestCode == pickFile) {
            runCatching { contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }
                .onSuccess { importText(it) }
                .onFailure { toast("Could not read file: ${it.message}") }
        }
    }

    private fun askImportUrl() {
        val field = EditText(this).apply { hint = "https://example.com/favorites.txt"; setSingleLine(); setPadding(dp(24), dp(12), dp(24), dp(12)) }
        AlertDialog.Builder(this).setTitle("Import from URL").setView(field)
            .setPositiveButton("Import") { _, _ ->
                var link = field.text.toString().trim()
                if (!link.startsWith("http", true)) link = "https://$link"
                Thread {
                    runCatching {
                        val c = URL(link).openConnection() as HttpURLConnection
                        c.connectTimeout = 15000; c.readTimeout = 15000
                        check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
                        c.inputStream.bufferedReader().use { it.readText() }
                    }.onSuccess { runOnUiThread { importText(it) } }
                        .onFailure { runOnUiThread { toast("Import failed: ${it.message}") } }
                }.start()
            }.setNegativeButton("Cancel", null).show()
    }

    // ---- Export (same "Name | URL" format that Import reads) ----
    private fun favoritesText() = "# SideLoader favorites\n" +
        favs.all().joinToString("\n") { "${it.name} | ${it.url}" } + "\n"

    private fun showExport() {
        if (favs.all().isEmpty()) { toast("No favorites to export"); return }
        AlertDialog.Builder(this).setTitle("Export favorites")
            .setItems(arrayOf("Save to a file", "Save to Downloads folder")) { _, i ->
                if (i == 0) {
                    try {
                        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain")
                            .putExtra(Intent.EXTRA_TITLE, "sideloader-favorites.txt"), saveFile)
                    } catch (e: ActivityNotFoundException) {
                        toast("No file picker on this device. Use \"Save to Downloads folder\" instead.")
                    }
                } else {
                    val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
                    runCatching { File(dir, "sideloader-favorites.txt").writeText(favoritesText()) }
                        .onSuccess { toast("Saved sideloader-favorites.txt to Downloads") }
                        .onFailure { toast("Export failed: ${it.message}") }
                }
            }.show()
    }

    private fun importText(text: String) {
        val found = Favorites.parse(text)
        val added = favs.addAll(found)
        toast(if (found.isEmpty()) "No valid URLs found in that file"
              else "Imported $added new (${found.size - added} duplicates skipped)")
    }

    // ---- Downloads ----
    private fun fetch(url: String, disposition: String?) {
        status.text = "Downloading…"; bar.progress = 0
        Downloads.start(this, url, disposition,
            { p -> runOnUiThread { bar.progress = p } },
            { r -> runOnUiThread {
                r.onSuccess { f ->
                    status.text = "Saved ${f.name}"
                    if (f.extension.equals("apk", true)) installApk(f)
                }.onFailure { status.text = "Failed: ${it.message}" }
            } })
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (web.canGoBack()) web.goBack() else super.onBackPressed() }
}
