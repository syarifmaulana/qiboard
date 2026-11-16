package app.myboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class SettingsActivity : Activity() {
    private lateinit var info: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val d = resources.displayMetrics.density
        val pad = (20 * d).toInt()
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(pad, pad, pad, pad)

        fun btn(t: String, a: () -> Unit) {
            val bt = Button(this)
            bt.text = t
            bt.isAllCaps = false
            bt.setOnClickListener { a() }
            col.addView(bt)
        }

        val title = TextView(this)
        title.text = "MyBoard"
        title.textSize = 24f
        col.addView(title)

        btn("1. Aktifkan keyboard") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        btn("2. Pilih MyBoard sebagai keyboard") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        btn("3. Pilih folder sinkron (Inbox.md)") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            i.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
            startActivityForResult(i, 1)
        }

        info = TextView(this)
        col.addView(info)

        val sw = Switch(this)
        sw.text = "Baris emoji"
        sw.isChecked = Store.emoji(this)
        sw.setOnCheckedChangeListener { _, c -> Store.setEmoji(this, c) }
        col.addView(sw)

        val et = EditText(this)
        et.hint = "Ketik di sini untuk mencoba"
        col.addView(et)

        val sv = ScrollView(this)
        sv.addView(col)
        setContentView(sv)
        refresh()
    }

    private fun refresh() {
        info.text = "Folder: " + (Store.folder(this) ?: "belum dipilih (clip tetap tersimpan di HP)")
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(r: Int, c: Int, d: Intent?) {
        super.onActivityResult(r, c, d)
        val u = d?.data
        if (r == 1 && c == RESULT_OK && u != null) {
            contentResolver.takePersistableUriPermission(
                u,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            Store.setFolder(this, u.toString())
            refresh()
        }
    }
}
