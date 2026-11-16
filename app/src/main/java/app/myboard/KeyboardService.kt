package app.myboard

import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private val BG = 0xFF292D32.toInt()
private val KEY = 0xFF484C50.toInt()
private val KEYP = 0xFF5C6065.toInt()
private val SPEC = 0xFF3C4045.toInt()
private val SPEC2 = 0xFF3B3F43.toInt()
private val PILL = 0xFF34383C.toInt()
private val BLUE = 0xFF5B99F5.toInt()
private val FG = 0xFFE8EAED.toInt()
private val IC = 0xFFC4C7CC.toInt()
private val TB = 0xFFAEB1B5.toInt()
private val LINE = 0xFF44484D.toInt()
private val ACC = 0xFF8AB4F8.toInt()
private val MUTED = 0xFF80868B.toInt()
private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

private val EMOJI = listOf("😂", "😘", "😀", "❤️", "😭", "🤣", "😋", "🙏", "☺️", "😍")
private val ABC = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
private val SYM = listOf("@#$%&-+()/", "<>[]{}^_|", "*\"':;!?")

class KeyboardService : InputMethodService() {
    private var u = 1f
    private lateinit var root: LinearLayout
    private lateinit var cm: ClipboardManager
    private val ui = Handler(Looper.getMainLooper())
    private var page = 0
    private var symbols = false
    private var shift = false
    private var edit = false
    private var lastLen = 0
    private var shiftKey: View? = null

    private val repeater = object : Runnable {
        override fun run() {
            del()
            ui.postDelayed(this, 45)
        }
    }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        val clip = cm.primaryClip
        val t = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this).toString().trim() else ""
        if (t.isNotEmpty() && !isPasswordField()) Store.add(this, t)
    }

    override fun onCreate() {
        super.onCreate()
        cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        cm.removePrimaryClipChangedListener(clipListener)
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        val dm = resources.displayMetrics
        u = min(dm.widthPixels, dm.heightPixels) / 1080f
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setBackgroundColor(BG)
        render()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        shift = false
        if (::root.isInitialized && page != 0) {
            page = 0
            render()
        }
    }

    private fun p(v: Float) = (v * u).roundToInt()

    private fun isPasswordField(): Boolean {
        val t = currentInputEditorInfo?.inputType ?: return false
        val cls = t and InputType.TYPE_MASK_CLASS
        val v = t and InputType.TYPE_MASK_VARIATION
        val textPw = cls == InputType.TYPE_CLASS_TEXT && (
            v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
        val numPw = cls == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        return textPw || numPw
    }

    private fun commit(s: String) {
        currentInputConnection?.commitText(s, 1)
        lastLen = s.length
    }

    private fun del() = sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)

    private fun typeChar(c: String) {
        commit(if (shift && !symbols) c.uppercase() else c)
        if (shift) {
            shift = false
            shiftKey?.background = bg(SPEC, 22f)
        }
    }

    private fun enter() {
        val opt = currentInputEditorInfo?.imeOptions ?: 0
        val a = opt and EditorInfo.IME_MASK_ACTION
        val noEnter = (opt and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnter && a != EditorInfo.IME_ACTION_NONE && a != EditorInfo.IME_ACTION_UNSPECIFIED) {
            currentInputConnection?.performEditorAction(a)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    private fun sendFieldToInbox() {
        val ic = currentInputConnection ?: return
        val before = (ic.getTextBeforeCursor(2000, 0) ?: "").toString()
        val after = (ic.getTextAfterCursor(2000, 0) ?: "").toString()
        val txt = (before + after).trim()
        if (txt.isNotEmpty()) Store.add(this, txt)
    }

    private fun bg(color: Int, r: Float): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = p(r).toFloat()
        return g
    }

    private fun label(s: String, sizeU: Float, color: Int, g: Int = Gravity.CENTER): TextView {
        val t = TextView(this)
        t.text = s
        t.setTextColor(color)
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, p(sizeU).toFloat())
        t.gravity = g
        t.includeFontPadding = false
        return t
    }

    private fun iconView(res: Int, wU: Float, hU: Float, tint: Int, sizeU: Float): ImageView {
        val v = ImageView(this)
        v.setImageResource(res)
        v.setColorFilter(tint)
        v.scaleType = ImageView.ScaleType.FIT_CENTER
        v.setPadding((p(wU) - p(sizeU)) / 2, (p(hU) - p(sizeU)) / 2, (p(wU) - p(sizeU)) / 2, (p(hU) - p(sizeU)) / 2)
        return v
    }

    private fun LinearLayout.put(v: View, w: Int, h: Int, weight: Float = 0f, gap: Float = 12f) {
        val lp = LinearLayout.LayoutParams(w, h, weight)
        if (childCount > 0) lp.leftMargin = p(gap)
        addView(v, lp)
    }

    private fun LinearLayout.addRow(h: Float, mb: Float, pad: Float, build: LinearLayout.() -> Unit) {
        val r = LinearLayout(context)
        r.orientation = LinearLayout.HORIZONTAL
        r.setPadding(p(pad), 0, p(12f), 0)
        r.build()
        val lp = LinearLayout.LayoutParams(p(1080f), p(h))
        lp.bottomMargin = p(mb)
        addView(r, lp)
    }

    private fun touchKey(
        v: View, normal: Int, r: Float,
        down: (MotionEvent) -> Unit = {},
        move: (MotionEvent) -> Unit = {},
        up: (MotionEvent) -> Unit = {},
        cancel: () -> Unit = {}
    ): View {
        v.background = bg(normal, r)
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.background = bg(KEYP, r)
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    down(e)
                }
                MotionEvent.ACTION_MOVE -> move(e)
                MotionEvent.ACTION_UP -> {
                    view.background = bg(normal, r)
                    up(e)
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.background = bg(normal, r)
                    cancel()
                }
            }
            true
        }
        return v
    }

    private fun clickKey(v: View, normal: Int, r: Float, onClick: () -> Unit): View {
        v.background = bg(normal, r)
        v.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        }
        return v
    }

    private fun render() {
        root.removeAllViews()
        shiftKey = null
        val w = p(1080f)
        if (Store.emoji(this)) root.addView(emojiStrip(), LinearLayout.LayoutParams(w, p(104f)))
        root.addView(toolbar(), LinearLayout.LayoutParams(w, p(146f)))
        val area = FrameLayout(this)
        when (page) {
            0 -> keysPanel(area)
            1 -> menuPanel(area)
            else -> clipPanel(area)
        }
        root.addView(area, LinearLayout.LayoutParams(w, p(695f)))
        root.addView(View(this), LinearLayout.LayoutParams(w, p(30f)))
    }

    private fun emojiStrip(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        EMOJI.forEachIndexed { i, e ->
            val t = label(e, 40f, FG)
            t.setOnClickListener { commit(e) }
            val lp = LinearLayout.LayoutParams(p(110f), p(102f))
            if (i == 0) lp.leftMargin = p(-8f)
            r.addView(t, lp)
        }
        col.addView(r, LinearLayout.LayoutParams(p(1080f), p(102f)))
        val line = View(this)
        line.setBackgroundColor(LINE)
        col.addView(line, LinearLayout.LayoutParams(p(1080f), p(2f)))
        return col
    }

    private fun tbIcon(res: Int, tint: Int): View {
        val f = FrameLayout(this)
        f.addView(ImageView(this).apply {
            setImageResource(res)
            setColorFilter(tint)
        }, FrameLayout.LayoutParams(p(54f), p(54f), Gravity.CENTER))
        return f
    }

    private fun tbText(s: String): View {
        val f = FrameLayout(this)
        f.addView(label(s, 50f, TB), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        return f
    }

    private fun toolbar(): View {
        val f = FrameLayout(this)
        val cx = floatArrayOf(63f, 222f, 380f, 539f, 697f, 855f)
        for (i in 0..5) {
            if (i == 0 && page != 0) {
                val c = FrameLayout(this)
                val oval = GradientDrawable()
                oval.shape = GradientDrawable.OVAL
                oval.setColor(0xFF3A3E43.toInt())
                c.background = oval
                c.addView(ImageView(this).apply {
                    setImageResource(R.drawable.ic_close)
                    setColorFilter(FG)
                }, FrameLayout.LayoutParams(p(50f), p(50f), Gravity.CENTER))
                c.setOnClickListener { page = 0; edit = false; render() }
                val lp = FrameLayout.LayoutParams(p(93f), p(93f))
                lp.leftMargin = p(17f)
                lp.topMargin = p(12f)
                f.addView(c, lp)
                continue
            }
            val v: View = when (i) {
                0 -> tbIcon(R.drawable.ic_grid, TB).also { it.setOnClickListener { page = 1; render() } }
                1 -> tbText("⚙").also {
                    it.setOnClickListener {
                        startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
                2 -> tbText("⌨").also {
                    it.setOnClickListener {
                        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                    }
                }
                3 -> tbText("文A")
                4 -> tbText("‹I›")
                else -> tbIcon(R.drawable.ic_clip, if (page == 2) ACC else TB).also {
                    it.setOnClickListener { page = if (page == 2) 0 else 2; edit = false; render() }
                }
            }
            val lp = FrameLayout.LayoutParams(p(120f), p(80f))
            lp.leftMargin = p(cx[i] - 60f)
            lp.topMargin = p(22f)
            f.addView(v, lp)
        }
        return f
    }

    private fun charKey(c: String): View {
        val t = label(if (symbols) c else c.uppercase(), 56f, FG)
        return touchKey(t, KEY, 22f, down = { typeChar(c) })
    }

    private fun dotKey(c: String): View {
        val t = label(c, 56f, IC, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM)
        t.setPadding(0, 0, 0, p(20f))
        return touchKey(t, SPEC2, 22f, down = { commit(c) })
    }

    private fun keysPanel(area: FrameLayout) {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val L = if (symbols) SYM else ABC
        val kw = p(94.8f)
        val ww = p(148.8f)

        col.addRow(115f, 19f, 12f) {
            for (c in "1234567890") put(charKey(c.toString()), kw, p(115f))
        }
        col.addRow(121f, 22f, 12f) {
            for (c in L[0]) put(charKey(c.toString()), kw, p(121f))
        }
        col.addRow(121f, 22f, 65.4f) {
            for (c in L[1]) put(charKey(c.toString()), kw, p(121f))
        }
        col.addRow(121f, 23f, 12f) {
            if (symbols) {
                put(label("=\\<", 40f, IC).also { it.background = bg(SPEC, 22f) }, ww, p(121f))
            } else {
                val sk = iconView(R.drawable.ic_shift, 148.8f, 121f, IC, 56f)
                clickKey(sk, if (shift) 0xFF6B7075.toInt() else SPEC, 22f) {
                    shift = !shift
                    sk.background = bg(if (shift) 0xFF6B7075.toInt() else SPEC, 22f)
                }
                shiftKey = sk
                put(sk, ww, p(121f))
            }
            for (c in L[2]) put(charKey(c.toString()), kw, p(121f))
            val bk = iconView(R.drawable.ic_backspace, 148.8f, 121f, IC, 60f)
            touchKey(bk, SPEC, 22f,
                down = { del(); ui.postDelayed(repeater, 400) },
                up = { ui.removeCallbacks(repeater) },
                cancel = { ui.removeCallbacks(repeater) })
            put(bk, ww, p(121f))
        }
        col.addRow(131f, 0f, 12f) {
            val pill = label(if (symbols) "ABC" else "?123", 36f, IC)
            clickKey(pill, PILL, 66f) { symbols = !symbols; render() }
            put(pill, ww, p(131f))
            put(dotKey(","), kw, p(131f))
            val em = label("☺", 60f, IC)
            em.background = bg(KEY, 22f)
            put(em, kw, p(131f))

            var sx = 0f
            var moved = false
            val sp = View(context)
            touchKey(sp, KEY, 22f,
                down = { sx = it.x; moved = false },
                move = { e ->
                    val st = p(36f)
                    val d = e.x - sx
                    if (abs(d) >= st) {
                        moved = true
                        val n = (abs(d) / st).toInt()
                        val key = if (d > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                        repeat(n) { sendDownUpKeyEvents(key) }
                        sx += (if (d > 0) 1 else -1) * n * st
                    }
                },
                up = { if (!moved) commit(" ") })
            put(sp, 0, p(131f), 1f)

            put(dotKey("."), kw, p(131f))
            val en = iconView(R.drawable.ic_enter, 148.8f, 131f, Color.WHITE, 64f)
            clickKey(en, BLUE, 66f) { enter() }
            put(en, ww, p(131f))
        }
        area.addView(col, FrameLayout.LayoutParams(WRAP, WRAP))
    }

    private class Item(val ic: String, val name: String, val chev: Boolean, val act: () -> Unit)

    private fun menuPanel(area: FrameLayout) {
        val items = listOf(
            Item("◐", "Theme", true) {},
            Item("▤", "Sticker", true) {},
            Item("GIF", "GIF", true) {},
            Item("☺", "Emoji", true) { Store.setEmoji(this, !Store.emoji(this)); page = 0; render() },
            Item("▯", "One-handed", false) {},
            Item("⇪", "Send to Inbox", false) { sendFieldToInbox(); page = 0; render() },
            Item("⤢", "Resize", false) {},
            Item("↶", "Undo", false) {
                repeat(lastLen) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) }
                lastLen = 0
                page = 0
                render()
            }
        )
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        items.chunked(2).forEach { pair ->
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            pair.forEach { it2 -> r.put(pill(it2), p(462f), p(125f), gap = 23f) }
            val lp = LinearLayout.LayoutParams(WRAP, WRAP)
            lp.bottomMargin = p(22f)
            col.addView(r, lp)
        }
        val lp = FrameLayout.LayoutParams(WRAP, WRAP)
        lp.topMargin = p(28f)
        lp.leftMargin = p(66f)
        area.addView(col, lp)
        pencil(area, false) {}
    }

    private fun pill(it: Item): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(p(54f), 0, p(50f), 0)
        r.background = bg(KEY, 63f)
        r.addView(label(it.ic, 48f, IC), LinearLayout.LayoutParams(p(60f), WRAP))
        val n = label(it.name, 42f, FG, Gravity.CENTER_VERTICAL)
        val nlp = LinearLayout.LayoutParams(0, WRAP, 1f)
        nlp.leftMargin = p(40f)
        r.addView(n, nlp)
        if (it.chev) r.addView(label("›", 56f, MUTED), LinearLayout.LayoutParams(WRAP, WRAP))
        r.setOnClickListener { _ -> it.act() }
        return r
    }

    private fun pencil(area: FrameLayout, active: Boolean, onClick: () -> Unit) {
        val f = FrameLayout(this)
        val g = GradientDrawable()
        g.setColor(if (active) 0xFF3A3E43.toInt() else Color.TRANSPARENT)
        g.setStroke(p(4f), if (active) ACC else 0xFF9AA0A6.toInt())
        g.cornerRadius = p(43f).toFloat()
        f.background = g
        f.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_edit)
            setColorFilter(if (active) ACC else 0xFF9AA0A6.toInt())
        }, FrameLayout.LayoutParams(p(50f), p(50f), Gravity.CENTER))
        f.setOnClickListener { onClick() }
        val lp = FrameLayout.LayoutParams(p(196f), p(85f), Gravity.TOP or Gravity.END)
        lp.topMargin = p(619f)
        lp.rightMargin = p(67f)
        area.addView(f, lp)
    }

    private fun clipPanel(area: FrameLayout) {
        val all = Store.clips(this)
        val show = all.sortedByDescending { it.p }.take(8)
        if (show.isEmpty()) {
            area.addView(
                label("Belum ada clip. Salin teks, otomatis muncul di sini.", 38f, MUTED),
                FrameLayout.LayoutParams(MATCH, p(300f))
            )
        }
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        show.chunked(2).forEach { pair ->
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            pair.forEach { c -> r.put(card(c, all), p(462f), p(130f), gap = 23f) }
            val lp = LinearLayout.LayoutParams(WRAP, WRAP)
            lp.bottomMargin = p(20f)
            col.addView(r, lp)
        }
        val lp = FrameLayout.LayoutParams(WRAP, WRAP)
        lp.topMargin = p(28f)
        lp.leftMargin = p(66f)
        area.addView(col, lp)
        pencil(area, edit) { edit = !edit; render() }
    }

    private fun card(c: Clip, all: MutableList<Clip>): View {
        val f = FrameLayout(this)
        f.background = bg(if (edit) 0xFF5A3A3A.toInt() else KEY, 36f)
        val t = label(c.t, 36f, FG, Gravity.CENTER_VERTICAL)
        t.maxLines = 2
        t.ellipsize = TextUtils.TruncateAt.END
        t.setPadding(p(36f), 0, p(100f), 0)
        f.addView(t, FrameLayout.LayoutParams(MATCH, MATCH))
        val pin = iconView(R.drawable.ic_pin, 64f, 64f, if (c.p) ACC else MUTED, 44f)
        pin.setOnClickListener { c.p = !c.p; Store.save(this, all); render() }
        val plp = FrameLayout.LayoutParams(p(64f), p(64f), Gravity.END or Gravity.CENTER_VERTICAL)
        plp.rightMargin = p(20f)
        f.addView(pin, plp)
        f.setOnClickListener {
            if (edit) {
                all.remove(c)
                Store.save(this, all)
                render()
            } else {
                commit(c.t)
                page = 0
                render()
            }
        }
        return f
    }
}
