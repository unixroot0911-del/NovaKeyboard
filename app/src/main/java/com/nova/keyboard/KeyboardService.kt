package com.nova.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView

class KeyboardService : InputMethodService() {
    private enum class Mode { AR, EN, SYM, EMO }

    private var mode = Mode.AR
    private var shift = false
    private var dark = true
    private var haptic = true
    private lateinit var root: LinearLayout
    private val handler = Handler(Looper.getMainLooper())

    private val ar = listOf(
        "ضصثقفغعهخحجد".map { "$it" }, "شسيبلاتنمكط".map { "$it" }, "ئءؤرىةوزظذ".map { "$it" })
    private val arShift = listOf(
        listOf("َ", "ُ", "ِ", "ً", "ٌ", "ٍ", "ّ", "ْ", "ـ", "،", "؛", "؟"),
        listOf("إ", "أ", "آ", "ٰ", "«", "»", "ڤ", "پ", "چ", "گ", "ژ"),
        listOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩"))
    private val en = listOf(
        "qwertyuiop".map { "$it" }, "asdfghjkl".map { "$it" }, "zxcvbnm".map { "$it" })
    private val sym = listOf(
        "1234567890".map { "$it" },
        listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/"),
        listOf("*", "\"", "'", ":", ";", "!", "?", "_", "="))
    private val emojis = ("😀😁😂🤣😊😍😘😎🤔😢😭😡👍👎👏🙏💪🔥❤️💔🎉✨🌹☀️🌙⭐🍕☕🎂🚗✈️🏠📱💻🎧📷🎁💰✅❌⚠️🇸🇦🇪🇬🇦🇪🇮🇶🇰🇼")
        .let { s -> Regex("\\X").findAll(s).map { it.value }.toList() }

    private val bg get() = if (dark) 0xFF1B1B1F.toInt() else 0xFFE8EAED.toInt()
    private val keyBg get() = if (dark) 0xFF35363B.toInt() else 0xFFFFFFFF.toInt()
    private val keySp get() = if (dark) 0xFF4A4C52.toInt() else 0xFFCDD1D6.toInt()
    private val fg get() = if (dark) 0xFFFFFFFF.toInt() else 0xFF111111.toInt()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreateInputView(): View {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(3), dp(3), dp(3), dp(3)) }
        render()
        return root
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val p = getSharedPreferences("nova", MODE_PRIVATE)
        dark = p.getBoolean("dark", true); haptic = p.getBoolean("haptic", true)
        if (::root.isInitialized) render()
    }

    private fun render() {
        root.removeAllViews(); root.setBackgroundColor(bg)
        root.addView(row(listOf(
            key("📋", 1f, true) { pasteClip() },
            key("✂", 1f, true) { currentInputConnection?.performContextMenuAction(android.R.id.selectAll) },
            key(if (dark) "☀" else "🌙", 1f, true) {
                dark = !dark; getSharedPreferences("nova", MODE_PRIVATE).edit().putBoolean("dark", dark).apply(); render()
            },
            key("⌫ كلمة", 2f, true) { deleteWord() })))
        if (mode == Mode.EMO) {
            emojis.chunked(8).take(4).forEach { r -> root.addView(row(r.map { e -> key(e, 1f) { commit(e) } })) }
        } else {
            val rows = when (mode) {
                Mode.AR -> if (shift) arShift else ar
                Mode.EN -> en; else -> sym
            }
            root.addView(row(rows[0].map { k -> letter(k) }))
            root.addView(row(rows[1].map { k -> letter(k) }))
            val r3 = mutableListOf<View>()
            if (mode != Mode.SYM) r3.add(key(if (shift) "⬆" else "⇧", 1.5f, true) { shift = !shift; render() })
            rows[2].forEach { r3.add(letter(it)) }
            r3.add(key("⌫", 1.5f, true, true) { backspace() })
            root.addView(row(r3))
        }
        root.addView(row(listOf(
            key(if (mode == Mode.SYM) "ABC" else "123", 1.3f, true) { mode = if (mode == Mode.SYM) Mode.AR else Mode.SYM; shift = false; render() },
            key("🌐", 1f, true) { mode = if (mode == Mode.AR) Mode.EN else Mode.AR; shift = false; render() },
            key("😀", 1f, true) { mode = if (mode == Mode.EMO) Mode.AR else Mode.EMO; render() },
            key(if (mode == Mode.AR) "مسافة" else "space", 4f) { commit(" ") },
            key("،", 1f, true) { commit(if (mode == Mode.EN) "," else "،") },
            key(".", 1f, true) { commit(".") },
            key("↵", 1.5f, true) { enter() })))
    }

    private fun letter(k: String): View {
        val label = if (mode == Mode.EN && shift) k.uppercase() else k
        return key(label, 1f) {
            commit(label)
            if (mode == Mode.EN && shift) { shift = false; render() }
        }
    }

    private fun row(v: List<View>) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        v.forEach { addView(it) }
    }

    private fun key(label: String, w: Float, special: Boolean = false, repeat: Boolean = false, onTap: () -> Unit): View {
        val tv = TextView(this).apply {
            text = label; gravity = Gravity.CENTER; textSize = 20f; setTextColor(fg)
            background = GradientDrawable().apply { cornerRadius = dp(7).toFloat(); setColor(if (special) keySp else keyBg) }
            layoutParams = LinearLayout.LayoutParams(0, dp(48), w).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) }
        }
        val rep = object : Runnable { override fun run() { onTap(); handler.postDelayed(this, 50) } }
        tv.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.alpha = 0.6f
                    if (haptic) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onTap()
                    if (repeat) handler.postDelayed(rep, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { v.alpha = 1f; handler.removeCallbacks(rep) }
            }
            true
        }
        return tv
    }

    private fun commit(s: String) { currentInputConnection?.commitText(s, 1) }
    private fun backspace() {
        val ic = currentInputConnection ?: return
        if (ic.getSelectedText(0).isNullOrEmpty()) ic.deleteSurroundingText(1, 0) else ic.commitText("", 1)
    }
    private fun deleteWord() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(50, 0)?.toString() ?: return
        val trimmed = before.trimEnd()
        val n = before.length - (trimmed.lastIndexOf(' ') + 1)
        ic.deleteSurroundingText(maxOf(n, 1), 0)
    }
    private fun enter() {
        val ic = currentInputConnection ?: return
        val action = currentInputEditorInfo?.imeOptions?.and(android.view.inputmethod.EditorInfo.IME_MASK_ACTION) ?: 0
        if (action != 0 && action != android.view.inputmethod.EditorInfo.IME_ACTION_NONE &&
            (currentInputEditorInfo.imeOptions and android.view.inputmethod.EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0)
            ic.performEditorAction(action)
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
    }
    private fun pasteClip() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val t = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        if (!t.isNullOrEmpty()) commit(t)
    }
}
