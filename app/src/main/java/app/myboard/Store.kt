package app.myboard

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Clip(val t: String, var p: Boolean)

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("kb", 0)

    fun emoji(c: Context) = sp(c).getBoolean("emoji", true)
    fun setEmoji(c: Context, v: Boolean) = sp(c).edit().putBoolean("emoji", v).apply()
    fun folder(c: Context): String? = sp(c).getString("folder", null)
    fun setFolder(c: Context, v: String) = sp(c).edit().putString("folder", v).apply()

    fun clips(c: Context): MutableList<Clip> {
        val a = JSONArray(sp(c).getString("clips", "[]"))
        return MutableList(a.length()) {
            val o = a.getJSONObject(it)
            Clip(o.getString("t"), o.optBoolean("p"))
        }
    }

    fun save(c: Context, l: List<Clip>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("t", it.t).put("p", it.p)) }
        sp(c).edit().putString("clips", a.toString()).apply()
    }

    fun add(c: Context, t: String) {
        if (sp(c).getString("last", null) == t) return
        sp(c).edit().putString("last", t).apply()
        val l = clips(c)
        val old = l.firstOrNull { it.t == t }
        l.removeAll { it.t == t }
        l.add(0, Clip(t, old?.p ?: false))
        while (l.size > 80) {
            val i = l.indexOfLast { !it.p }
            if (i < 0) break
            l.removeAt(i)
        }
        save(c, l)
        appendInbox(c, t)
    }

    private fun appendInbox(c: Context, text: String) {
        val tree = folder(c) ?: return
        Thread {
            try {
                val treeUri = Uri.parse(tree)
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                val kids = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                var file: Uri? = null
                c.contentResolver.query(
                    kids,
                    arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null, null, null
                )?.use { q ->
                    while (q.moveToNext()) {
                        if (q.getString(1) == "Inbox.md") {
                            file = DocumentsContract.buildDocumentUriUsingTree(treeUri, q.getString(0))
                            break
                        }
                    }
                }
                if (file == null) {
                    file = DocumentsContract.createDocument(c.contentResolver, parent, "application/octet-stream", "Inbox.md")
                }
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                val line = "- [ ] " + stamp + " " + text.replace("\n", " ") + "\n"
                c.contentResolver.openOutputStream(file!!, "wa")?.use { it.write(line.toByteArray()) }
            } catch (e: Exception) {
            }
        }.start()
    }
}
