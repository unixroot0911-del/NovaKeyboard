package com.nova.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.LocalTime
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.security.SecureRandom
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.hypot

private fun blend(a: Int, b: Int, t: Float): Int {
    fun ch(sh: Int) = (((a shr sh) and 255) * (1 - t) + ((b shr sh) and 255) * t).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

class KeyboardService : InputMethodService() {
    private enum class Mode { AR, EN, SYM, SYM2, EMO, CLIP, TOOLS, STYLE, DATE, SNIP, PHR, KAO, COLOR }
    private class Th(val bg: Int, val key: Int, val sp: Int, val fg: Int, val tx: Int = fg) {
        val acc: Int = blend(sp, fg, 0.30f)
        val accFg: Int = fg
    }

    private fun col(x: Long) = x.toInt()
    private val themes = listOf(
        Th(col(0xFF101012), col(0xFF3B3C43), col(0xFF5A5C66), col(0xFFFFFFFF)),
        Th(col(0xFFD9DDE2), col(0xFFFFFFFF), col(0xFFB4BAC3), col(0xFF111111)),
        Th(col(0xFF000000), col(0xFF222225), col(0xFF45464C), col(0xFFFFFFFF)),
        Th(col(0xFF0A1F30), col(0xFF18486B), col(0xFF2F72A0), col(0xFFEAF6FF)),
        Th(col(0xFF24101A), col(0xFF4D2738), col(0xFF7A4560), col(0xFFFFEEF4))
    )

    private var mode = Mode.AR
    private var letterMode = Mode.AR
    private var shift = 0
    private var lastShiftTap = 0L
    private var lastSpace = 0L
    private var emojiCat = 1
    private var curH = 0
    private var repCount = 0
    private var undoOrig: String? = null
    private var undoNew: String? = null

    companion object {
        @Volatile var pendingVoice: String? = null
        @Volatile var instance: KeyboardService? = null
    }
    private lateinit var outer: FrameLayout
    private lateinit var root: LinearLayout
    private lateinit var cm: ClipboardManager
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("nova", MODE_PRIVATE) }
    private val th: Th get() = when {
        prefs.getBoolean("rainbow", false) -> rainbowThNow()
        prefs.getBoolean("custom", false) -> customTh()
        else -> themes[prefs.getInt("theme", 0).coerceIn(0, themes.size - 1)]
    }

    private var popup: PopupWindow? = null
    private var popAlts: List<String> = emptyList()
    private var popTv: List<TextView> = emptyList()
    private var popSel = 0
    private var popLeft = 0
    private var popItemW = 1

    private val ar = listOf(
        "ض ص ث ق ف غ ع ه خ ح ج د".split(" "),
        "ش س ي ب ل ا ت ن م ك ط".split(" "),
        "ئ ء ؤ ر لا ى ة و ز ظ".split(" "))
    private val arZ = listOf(
        "ض ص ث ق ف غ ع ه خ ح ج د".split(" "),
        "ش س ي ب ل ا ت ن م ك ط".split(" "),
        "ذ ئ ء ؤ ر لا ى ة و ز".split(" "))
    private val arAlpha = listOf(
        "ا ب ت ث ج ح خ د ذ ر ز".split(" "),
        "س ش ص ض ط ظ ع غ ف ق ك".split(" "),
        "ل م ن ه و ي ة ى ء".split(" "))
    private val arShift = listOf(
        "َ ُ ِ ً ٌ ٍ ّ ْ ـ ، ؛ ؟".split(" "),
        "إ أ آ ٰ « » ڤ پ چ گ ژ ٱ".split(" "),
        "٪ ٫ ٬ ﴾ ﴿ ‹ › … ۔".split(" "))
    private val en = listOf(
        "q w e r t y u i o p".split(" "),
        "a s d f g h j k l".split(" "),
        "z x c v b n m".split(" "))
    private val sym = listOf(
        "@ # $ _ & - + ( ) /".split(" "),
        "* \" ' : ; ! ? , . =".split(" "),
        "؟ ، ؛ « » ٪ …".split(" "))
    private val sym2 = listOf(
        "~ ` | • √ π ÷ × ¶ ∆".split(" "),
        "£ ¢ € ¥ ^ ° { } \\ ©".split(" "),
        "® ™ [ ] < > ±".split(" "))

    private val altMap: Map<String, List<String>> = mapOf(
        "ا" to listOf("أ", "إ", "آ", "ٱ"), "ي" to listOf("ى", "ئ"), "ه" to listOf("ة"),
        "و" to listOf("ؤ", "ء"), "لا" to listOf("لأ", "لإ", "لآ"), "د" to listOf("ذ"), "ز" to listOf("ذ"), "ك" to listOf("گ"),
        "ب" to listOf("پ"), "ج" to listOf("چ"), "ف" to listOf("ڤ"), "ط" to listOf("ظ"),
        "a" to listOf("à", "á", "â", "ä", "æ", "ã", "å", "ā"), "e" to listOf("è", "é", "ê", "ë", "ē"),
        "i" to listOf("ì", "í", "î", "ï"), "o" to listOf("ò", "ó", "ô", "ö", "œ"),
        "u" to listOf("ù", "ú", "û", "ü"), "c" to listOf("ç"), "n" to listOf("ñ"),
        "s" to listOf("ß", "š"), "y" to listOf("ý", "ÿ"),
        "-" to listOf("_", "–", "—", "•"), "$" to listOf("€", "£", "¥", "¢"),
        "?" to listOf("؟", "¿"), "!" to listOf("¡"), "'" to listOf("\"", "`"), "\"" to listOf("«", "»"))

    private val emojiData = listOf(
        "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 🤐 😐 😑 😶 😏 😒 🙄 😬 🤥 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🥵 🥶 😵 🤯 🤠 🥳 😎 🤓 🧐 😕 😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 💩 🤡 👻 👽 🤖",
        "👍 👎 👌 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 🤚 🖐 🖖 👋 🤝 🙏 ✍️ 💪 👏 🙌 👐 🤲 ❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💯 💢 💥 💫 💦 💨 🔥 ✨ ⭐ 🌟 🎉 🎊 👀 👁 👄 👅 🧠",
        "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🦅 🦋 🐝 🐢 🐍 🐙 🐬 🐳 🐪 🐎 🌹 🌸 🌻 🌴 🌲 🍀 🍎 🍌 🍉 🍇 🍓 🍒 🥑 🍕 🍔 🍟 🌭 🍗 🍣 🍩 🍰 🎂 🍫 ☕ 🍵 🥤 🍺 🍯 🥙 🍚",
        "⚽ 🏀 🏈 🎾 🏐 🎮 🎯 🎵 🎧 🎤 📱 💻 ⌚ 📷 🔋 💡 💰 💳 🎁 🔑 🔒 🔔 📌 📎 ✏️ 📚 ✅ ❌ ⚠️ ❓ ❗ ➕ ➖ ✔️ 🚗 ✈️ 🚀 🏠 🕌 🕋 🌙 ☀️ ⛅ 🌧 ⚡ ❄️ 🌍 ⏰ 📞 ✉️ 🔍",
        "🇸🇦 🇪🇬 🇦🇪 🇮🇶 🇰🇼 🇶🇦 🇧🇭 🇴🇲 🇯🇴 🇱🇧 🇸🇾 🇵🇸 🇾🇪 🇱🇾 🇹🇳 🇩🇿 🇲🇦 🇸🇩 🇸🇴 🇹🇷 🇺🇸 🇬🇧 🇫🇷 🇩🇪 🇮🇹 🇪🇸 🇨🇦 🇯🇵 🇰🇷 🇨🇳 🇮🇳 🇧🇷 🇷🇺 🇵🇰 🇮🇷"
    )

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClip() }

    override fun onCreate() {
        super.onCreate()
        instance = this
        cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.addPrimaryClipChangedListener(clipListener)
        letterMode = if (prefs.getInt("lang", 0) == 1) Mode.EN else Mode.AR
        mode = letterMode
    }

    override fun onDestroy() {
        cm.removePrimaryClipChangedListener(clipListener)
        stopAnim()
        instance = null
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode() = false

    override fun onCreateInputView(): View {
        outer = FrameLayout(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        outer.addView(root)
        render()
        return outer
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        captureClip()
        if (!restarting) {
            val t0 = info?.inputType ?: 0
            val cls = t0 and InputType.TYPE_MASK_CLASS
            val v = t0 and InputType.TYPE_MASK_VARIATION
            shift = 0
            mode = when {
                cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE -> Mode.SYM
                cls == InputType.TYPE_CLASS_TEXT && (v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                        v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                        v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                        v == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                        v == InputType.TYPE_TEXT_VARIATION_URI) -> Mode.EN
                else -> letterMode
            }
        }
        if (::root.isInitialized) render()
        if (!restarting) autoCap()
        flushVoice()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        dismissPopup()
        stopAnim()
        super.onFinishInputView(finishingInput)
    }

    // ---------- sizing ----------
    private fun keyH(): Int {
        val dm = resources.displayMetrics
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val rowsN = if (prefs.getBoolean("numrow", true)) 5.8f else 4.8f
        val pct = prefs.getInt("height", 44)
        val minH = if (land) dp(30) else dp(40)
        return (dm.heightPixels * pct / 100f / rowsN).toInt().coerceIn(minH, dp(78))
    }

    // ---------- rendering ----------
    private fun render() {
        dismissPopup()
        dismissPreview()
        rkeys.clear(); posReady = false
        val t = th
        val h = keyH()
        curH = h
        val dm = resources.displayMetrics
        val oh = prefs.getInt("onehand", 0)
        val w = if (oh == 0) FrameLayout.LayoutParams.MATCH_PARENT else (dm.widthPixels * 0.8f).toInt()
        val g = when (oh) { 1 -> Gravity.END; 2 -> Gravity.START; else -> Gravity.NO_GRAVITY }
        root.layoutParams = FrameLayout.LayoutParams(w, FrameLayout.LayoutParams.WRAP_CONTENT, g)
        outer.setBackgroundColor(t.bg)
        root.setPadding(dp(3), dp(2), dp(3), dp(6))
        root.removeAllViews()
        root.addView(toolbar(h))
        when (mode) {
            Mode.EMO -> emojiPanel(h)
            Mode.CLIP -> clipPanel(h)
            Mode.TOOLS -> toolsPanel(h)
            Mode.STYLE -> choicePanel(h, "✨ زخرفة وأنماط الخط",
                Tools.styles.map { (label, f) -> label to { applyStyle(f) } })
            Mode.DATE -> choicePanel(h, "📅 التاريخ والوقت", dateItems().map { s -> s to { insertText(s) } })
            Mode.SNIP -> snipPanel(h)
            Mode.COLOR -> colorPanel(h)
            Mode.PHR -> choicePanel(h, "🕌 عبارات ورموز", phraseItems().map { (l, x) -> l to { insertText(x) } })
            Mode.KAO -> choicePanel(h, "😜 كاموجي", kaoItems().map { x -> x to { insertText(x) } })
            else -> keysPanel(h)
        }
        if (rainbowOn()) startAnim() else stopAnim()
    }

    private fun toolbar(h: Int): View {
        val s = curH * 0.30f
        return row((h * 0.8f).toInt(), listOf(
            key("⚡", 1f, 2, sz = s) { mode = Mode.TOOLS; render() },
            key("📋", 1f, 1, sz = s) { mode = Mode.CLIP; render() },
            key("🎤", 1f, 1, sz = s) { startVoice() },
            key("◀", 1f, 1, repeat = true, sz = s) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT) },
            key("▶", 1f, 1, repeat = true, sz = s) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT) },
            key("🎨", 1f, 1, sz = s, onLong = { mode = Mode.COLOR; render() }) {
                prefs.edit().putBoolean("rainbow", false).putBoolean("custom", false)
                    .putInt("theme", (prefs.getInt("theme", 0) + 1) % themes.size).apply()
                ripples.clear(); render()
            },
            key("🤚", 1f, if (prefs.getInt("onehand", 0) != 0) 3 else 1, sz = s) {
                prefs.edit().putInt("onehand", (prefs.getInt("onehand", 0) + 1) % 3).apply(); render()
            },
            key("⚙", 1f, 1, sz = s) { openSettings() }
        ))
    }

    private fun keysPanel(h: Int) {
        if (prefs.getBoolean("numrow", true)) {
            root.addView(row(h, "1234567890".map { d ->
                key("$d", 1f, alts = listOf("٠١٢٣٤٥٦٧٨٩"[d - '0'].toString(), ")!@#\$%^&*("[d - '0'].toString())) { commit("$d"); afterText() }
            }))
        }
        val layout = prefs.getInt("arlayout", 0)
        val stdAr = mode == Mode.AR && shift == 0 && layout != 1
        val rows = when (mode) {
            Mode.AR -> if (shift != 0) arShift else when (layout) { 1 -> arAlpha; 2 -> arZ; else -> ar }
            Mode.EN -> en
            Mode.SYM -> sym
            else -> sym2
        }
        root.addView(row(h, rows[0].map { letterKey(it) }))
        val stagger = mode == Mode.EN || stdAr
        val r2 = mutableListOf<View>()
        if (stagger) r2.add(spacer(0.5f))
        rows[1].forEach { r2.add(letterKey(it)) }
        if (stagger) r2.add(spacer(0.5f))
        root.addView(row(h, r2))
        val sw = if (stdAr) 1.2f else 1.5f
        val r3 = mutableListOf<View>()
        when (mode) {
            Mode.AR -> r3.add(key("◌َ", sw, if (shift != 0) 3 else 1) { onShift() })
            Mode.EN -> r3.add(key(if (shift == 0) "⇧" else if (shift == 1) "⬆" else "⇪", sw, if (shift != 0) 3 else 1) { onShift() })
            Mode.SYM -> r3.add(key("=\\<", sw, 1) { mode = Mode.SYM2; render() })
            else -> r3.add(key("123", sw, 1) { mode = Mode.SYM; render() })
        }
        rows[2].forEach { r3.add(letterKey(it)) }
        r3.add(key("⌫", sw, 1, repeat = true, swipeDel = true) { backspace() })
        root.addView(row(h, r3))
        root.addView(bottomRow(h))
    }

    private fun bottomRow(h: Int): View {
        val isAr = letterMode == Mode.AR
        val toSym = mode == Mode.SYM || mode == Mode.SYM2
        return row(h, listOf(
            key(if (toSym) "ABC" else "123", 1.5f, 1) { mode = if (toSym) letterMode else Mode.SYM; render() },
            key("🌐", 1.2f, 1, onLong = { picker() }) {
                val to = if (mode == Mode.AR) Mode.EN else Mode.AR
                letterMode = to; mode = to; shift = 0
                prefs.edit().putInt("lang", if (to == Mode.EN) 1 else 0).apply()
                render(); autoCap()
            },
            key("😀", 1.2f, 1) { mode = Mode.EMO; render() },
            key(if (isAr) "،" else ",", 1f, 1,
                alts = if (isAr) listOf(",", "؛", "؟") else listOf("،", "؛")) { commit(if (isAr) "،" else ","); afterText() },
            key(if (isAr) "العربية" else "English", 4.2f, 0, onLong = { picker() }, swipeCursor = true) { space() },
            key(".", 1f, 1, alts = listOf("?", "!", ":", ";", "'", "\"", "؟")) { commit("."); afterText() },
            key(enterLabel(), 1.9f, 2) { enter() }
        ))
    }

    private fun letterKey(k: String): View {
        val up = mode == Mode.EN && shift != 0
        val isMark = k.length == 1 && Character.getType(k[0]) == Character.NON_SPACING_MARK.toInt()
        val lab = if (up) k.uppercase() else k
        val alts = (altMap[k] ?: emptyList()).map { if (up) it.uppercase() else it }
        return key(if (isMark) "◌$k" else lab, 1f, alts = alts) { commit(lab); afterText() }
    }

    private fun emojiPanel(h: Int) {
        val cats = listOf("🕘", "😀", "👍", "🐶", "⚽", "🇸🇦")
        root.addView(row(h, cats.mapIndexed { i, c ->
            key(c, 1f, if (i == emojiCat) 3 else 1, sz = curH * 0.38f) { emojiCat = i; render() }
        }))
        val list = if (emojiCat == 0) recents() else emojiData[emojiCat - 1].split(" ").filter { it.isNotEmpty() }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (list.isEmpty()) {
            col.addView(TextView(this).apply {
                text = "لا توجد إيموجي حديثة بعد"; setTextColor(th.tx); gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h * 3)
            })
        }
        list.chunked(8).forEach { r ->
            val views = r.map { e -> key(e, 1f, onUp = true, sz = curH * 0.42f) { commitEmoji(e) } }.toMutableList()
            for (i in r.size until 8) views.add(spacer(1f))
            col.addView(row(h, views))
        }
        root.addView(ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h * 3)
            isVerticalScrollBarEnabled = false
            addView(col)
        })
        root.addView(panelBottom(h))
    }

    private fun panelBottom(h: Int): View = row(h, listOf(
        key("ABC", 2f, 1) { mode = letterMode; render(); autoCap() },
        key(if (letterMode == Mode.AR) "العربية" else "English", 6f) { space() },
        key("⌫", 2f, 1, repeat = true, swipeDel = true) { backspace() }
    ))

    private fun clipPanel(h: Int) {
        val t = th
        val clips = loadClips()
        root.addView(row(h, listOf(
            key("⌨", 1.2f, 1) { mode = letterMode; render() },
            TextView(this).apply {
                text = "الحافظة · ${clips.size}"; setTextColor(t.tx); gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_PX, curH * 0.34f)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 3.6f)
            },
            key("🗑 مسح", 2f, 1) { saveClips(loadClips().filter { it.second }); render() }
        )))
        val colv = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (clips.isEmpty()) {
            colv.addView(TextView(this).apply {
                text = "لا يوجد شيء منسوخ بعد\nانسخ أي نص وسيظهر هنا"
                setTextColor(t.tx); gravity = Gravity.CENTER; alpha = 0.7f
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h * 3)
            })
        }
        clips.sortedByDescending { it.second }.forEach { (txt, pin) ->
            val card = TextView(this).apply {
                text = txt.replace("\n", " ")
                maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                setTextColor(t.fg); setTextSize(TypedValue.COMPLEX_UNIT_PX, curH * 0.27f)
                setPadding(dp(10), 0, dp(10), 0); gravity = Gravity.CENTER_VERTICAL
                background = rounded(t.key)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(dp(2), dp(3), dp(2), dp(3))
                }
                setOnClickListener { feedback(it); commit(txt) }
            }
            colv.addView(row(h, listOf(
                card,
                key("📌", 0.8f, if (pin) 3 else 1, onUp = true, sz = curH * 0.32f) { togglePin(txt); render() },
                key("✕", 0.8f, 1, onUp = true, sz = curH * 0.32f) { saveClips(loadClips().filter { it.first != txt }); render() }
            )))
        }
        root.addView(ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h * 3)
            isVerticalScrollBarEnabled = false
            addView(colv)
        })
        root.addView(panelBottom(h))
    }

    // ---------- building blocks ----------
    private fun row(h: Int, v: List<View>) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h)
        v.forEach { addView(it) }
    }

    private fun spacer(w: Float) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, w)
    }

    private fun rounded(c: Int, active: Boolean = false): Drawable {
        val t = th
        val r = dp(8).toFloat()
        val shadow = GradientDrawable().apply { cornerRadius = r; setColor(blend(c, 0xFF000000.toInt(), 0.45f)) }
        val face = GradientDrawable().apply {
            cornerRadius = r; setColor(c)
            if (active) setStroke(dp(2), t.fg) else setStroke(1, (t.fg and 0x00FFFFFF) or 0x33000000)
        }
        return LayerDrawable(arrayOf(shadow, face)).apply { setLayerInset(1, 0, 0, 0, dp(2)) }
    }

    private fun key(
        label: String, w: Float, style: Int = 0, repeat: Boolean = false,
        alts: List<String> = emptyList(), onLong: (() -> Unit)? = null,
        onUp: Boolean = false, sz: Float = 0f, swipeDel: Boolean = false, swipeCursor: Boolean = false,
        onAlt: ((String) -> Unit)? = null, onTap: () -> Unit
    ): View {
        val t = th
        val bgc = when (style) { 1 -> t.sp; 2, 3 -> t.acc; else -> t.key }
        val fgc = t.fg
        val tv = TextView(this)
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.setTextColor(fgc)
        tv.includeFontPadding = false
        tv.typeface = if (rainbowOn()) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else if (style != 0) Typeface.DEFAULT_BOLD else Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val px = if (sz > 0f) sz else if (label.codePointCount(0, label.length) > 2) curH * 0.27f else curH * 0.40f
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
        tv.background = rounded(bgc, style == 3)
        if (!onUp) {
            val ld = tv.background as LayerDrawable
            rkeys.add(RKey(tv, ld.getDrawable(1) as GradientDrawable, ld.getDrawable(0) as GradientDrawable, style))
        }
        val mx = maxOf(1, (dp(1) * 1.5f).toInt())
        tv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, w).apply {
            setMargins(mx, dp(3), mx, dp(3))
        }
        val deferred = alts.isNotEmpty() || onLong != null || onUp
        val showPrev = style == 0 && !onUp && !repeat && onLong == null && label.codePointCount(0, label.length) == 1
        var longFired = false
        var moved = false
        var downX = 0f
        var downY = 0f
        var swipeN = 0
        val longRun = Runnable {
            dismissPreview()
            if (alts.isNotEmpty()) { longFired = true; feedback(tv); showPopup(tv, alts) }
            else if (onLong != null) { longFired = true; feedback(tv); onLong() }
        }
        val rep = object : Runnable {
            override fun run() { repCount++; onTap(); handler.postDelayed(this, if (repCount > 8) 38L else 55L) }
        }
        tv.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    longFired = false; moved = false; swipeN = 0; downX = e.rawX; downY = e.rawY
                    v.alpha = 0.6f
                    if (!onUp) feedback(v)
                    if (showPrev) showPreview(v, label)
                    if (!onUp && rainbowOn() && prefs.getBoolean("ripple", true)) startRipple(v)
                    if (repeat) { repCount = 0; onTap(); handler.postDelayed(rep, 330) }
                    else if (!deferred) onTap()
                    else if (!onUp) handler.postDelayed(longRun, 330)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (popup != null) updatePopupSel(e.rawX)
                    else if (swipeDel) {
                        val n = ((downX - e.rawX) / dp(34)).toInt()
                        if (n > swipeN) { handler.removeCallbacks(rep); deleteWordBack(); swipeN = n; moved = true }
                    } else if (swipeCursor) {
                        val n = ((e.rawX - downX) / dp(16)).toInt()
                        if (n != swipeN) {
                            handler.removeCallbacks(longRun)
                            val d = n - swipeN
                            for (k in 0 until abs(d))
                                sendDownUpKeyEvents(if (d > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
                            swipeN = n; moved = true
                        }
                    } else if (onUp && (abs(e.rawX - downX) > dp(8) || abs(e.rawY - downY) > dp(8))) moved = true
                }
                MotionEvent.ACTION_UP -> {
                    v.alpha = 1f
                    handler.removeCallbacks(rep); handler.removeCallbacks(longRun)
                    dismissPreview()
                    if (deferred && !repeat) {
                        if (longFired) {
                            if (alts.isNotEmpty()) {
                                val s = popAlts.getOrNull(popSel)
                                dismissPopup()
                                if (s != null) { if (onAlt != null) onAlt(s) else { commit(s); afterText() } }
                            }
                        } else if (!moved) {
                            if (onUp) feedback(v)
                            onTap()
                        }
                    }
                    dismissPopup()
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1f
                    handler.removeCallbacks(rep); handler.removeCallbacks(longRun)
                    dismissPreview(); dismissPopup()
                }
            }
            true
        }
        return tv
    }

    // ---------- popup for alternates ----------
    private fun showPopup(anchor: View, alts: List<String>) {
        val t = th
        val itemW = maxOf(anchor.width, dp(38))
        val itemH = (anchor.height * 1.1f).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(t.sp); setStroke(dp(1), t.acc) }
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        popTv = alts.map { a ->
            TextView(this).apply {
                text = a; gravity = Gravity.CENTER; setTextColor(t.fg)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, itemH * 0.5f)
                layoutParams = LinearLayout.LayoutParams(itemW, itemH)
            }.also { box.addView(it) }
        }
        popAlts = alts; popSel = 0; highlight()
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val total = itemW * alts.size + dp(8)
        val x = (loc[0] + anchor.width / 2 - total / 2).coerceIn(0, maxOf(0, resources.displayMetrics.widthPixels - total))
        val y = loc[1] - itemH - dp(16)
        val pw = PopupWindow(box, total, itemH + dp(8))
        pw.isTouchable = false
        pw.isFocusable = false
        pw.isClippingEnabled = false
        pw.showAtLocation(root, Gravity.NO_GRAVITY, x, y)
        popup = pw; popLeft = x + dp(4); popItemW = itemW
    }

    private fun updatePopupSel(rawX: Float) {
        if (popAlts.isEmpty()) return
        val i = ((rawX - popLeft) / popItemW).toInt().coerceIn(0, popAlts.size - 1)
        if (i != popSel) { popSel = i; highlight() }
    }

    private fun highlight() {
        val t = th
        popTv.forEachIndexed { i, tv ->
            tv.background = if (i == popSel) rounded(t.acc) else null
            tv.setTextColor(if (i == popSel) t.accFg else t.fg)
        }
    }

    private fun dismissPopup() {
        try { popup?.dismiss() } catch (e: Throwable) {}
        popup = null; popAlts = emptyList()
    }

    // ---------- actions ----------
    private fun feedback(v: View) {
        if (prefs.getBoolean("haptic", true))
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        if (prefs.getBoolean("sound", false))
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager).playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
    }

    private fun commit(s: String) { currentInputConnection?.commitText(s, 1) }

    private fun commitEmoji(e: String) {
        commit(e)
        val l = (listOf(e) + recents().filter { it != e }).take(32)
        prefs.edit().putString("recent_emoji", l.joinToString(" ")).apply()
    }

    private fun recents(): List<String> =
        (prefs.getString("recent_emoji", "") ?: "").split(" ").filter { it.isNotEmpty() }

    private fun onShift() {
        val now = SystemClock.uptimeMillis()
        shift = if (mode == Mode.EN && shift != 0 && now - lastShiftTap < 350) 2 else if (shift == 0) 1 else 0
        lastShiftTap = now
        render()
    }

    private fun afterText() {
        if (mode == Mode.EN && shift == 1) { shift = 0; render(); return }
        autoCap()
    }

    private fun autoCap() {
        if (mode != Mode.EN || shift != 0 || !prefs.getBoolean("autocap", true)) return
        val ic = currentInputConnection ?: return
        val ty = currentInputEditorInfo?.inputType ?: 0
        if (ty == 0) return
        if (ic.getCursorCapsMode(ty) != 0) { shift = 1; render() }
    }

    private fun deleteWordBack(): Boolean {
        val ic = currentInputConnection ?: return false
        val b = ic.getTextBeforeCursor(60, 0)?.toString()
        if (b.isNullOrEmpty()) return false
        var i = b.length
        while (i > 0 && b[i - 1].isWhitespace()) i--
        while (i > 0 && !b[i - 1].isWhitespace()) i--
        ic.deleteSurroundingText(b.length - i, 0)
        return true
    }

    private fun backspace() {
        if (repCount > 18 && deleteWordBack()) return
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        autoCap()
    }

    private fun space() {
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        if (prefs.getBoolean("dblspace", true) && now - lastSpace < 400) {
            val b = ic.getTextBeforeCursor(2, 0)?.toString()
            if (b != null && b.length == 2 && b[1] == ' ' && !b[0].isWhitespace() && b[0] !in ".,!?؟،:;") {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                lastSpace = 0
                afterText()
                return
            }
        }
        ic.commitText(" ", 1)
        lastSpace = now
        afterText()
    }

    private fun enterLabel(): String {
        val o = currentInputEditorInfo?.imeOptions ?: 0
        if ((o and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return "↵"
        return when (o and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> "🔍"
            EditorInfo.IME_ACTION_GO -> "➜"
            EditorInfo.IME_ACTION_SEND -> "➤"
            EditorInfo.IME_ACTION_NEXT -> "⇥"
            EditorInfo.IME_ACTION_DONE -> "✓"
            else -> "↵"
        }
    }

    private fun enter() {
        val ic = currentInputConnection ?: return
        val o = currentInputEditorInfo?.imeOptions ?: 0
        val action = o and EditorInfo.IME_MASK_ACTION
        val noEnter = (o and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnter && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED)
            ic.performEditorAction(action)
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        shift = 0
        autoCap()
    }

    private fun picker() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ---------- clipboard history ----------
    private fun loadClips(): MutableList<Pair<String, Boolean>> {
        val out = mutableListOf<Pair<String, Boolean>>()
        try {
            val a = JSONArray(prefs.getString("clips", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(o.getString("t") to o.optBoolean("p", false))
            }
        } catch (e: Exception) {}
        return out
    }

    private fun saveClips(l: List<Pair<String, Boolean>>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("t", it.first).put("p", it.second)) }
        prefs.edit().putString("clips", a.toString()).apply()
    }

    private fun addClip(text: String) {
        val l = loadClips()
        val old = l.firstOrNull { it.first == text }
        l.removeAll { it.first == text }
        l.add(0, text to (old?.second == true))
        val res = mutableListOf<Pair<String, Boolean>>()
        var n = 0
        for (x in l) { if (x.second) res.add(x) else if (n < 40) { res.add(x); n++ } }
        saveClips(res)
    }

    private fun togglePin(text: String) {
        saveClips(loadClips().map { if (it.first == text) it.first to !it.second else it })
    }

    private fun captureClip() {
        try {
            if (prefs.getBoolean("incog", false)) return
            val d = cm.primaryClipDescription
            if (d?.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) return
            val t = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
            if (!t.isNullOrBlank() && t.length < 20000) addClip(t)
        } catch (e: Throwable) {}
    }

    // ---------- tools ----------
    private class Tg(val text: String, val delB: Int, val delA: Int)

    private val calcRe = Regex("[0-9٠-٩.,٫٬+\\-*/xX×÷^%()=\\s]+\\z")
    private val numRe = Regex("[0-9٠-٩][0-9٠-٩,٬]*\\z")

    private fun toast(m: String) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show() }

    private fun done() { mode = letterMode; render() }

    private fun getTg(chunk: Regex?): Tg? {
        val ic = currentInputConnection ?: return null
        val sel = ic.getSelectedText(0)?.toString()
        if (!sel.isNullOrEmpty()) return Tg(sel, 0, 0)
        val b = ic.getTextBeforeCursor(5000, 0)?.toString() ?: ""
        if (chunk == null) {
            val a = ic.getTextAfterCursor(5000, 0)?.toString() ?: ""
            val all = b + a
            return if (all.isEmpty()) null else Tg(all, b.length, a.length)
        }
        val m = chunk.find(b) ?: return null
        return Tg(m.value, m.value.length, 0)
    }

    private fun applyTg(tg: Tg, out: String) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        if (tg.delB > 0 || tg.delA > 0) ic.deleteSurroundingText(tg.delB, tg.delA)
        ic.commitText(out, 1)
        ic.endBatchEdit()
        undoOrig = tg.text
        undoNew = out
    }

    private fun toolConvert() {
        val tg = getTg(null) ?: return toast("اكتب نصاً أو حدّده أولاً")
        applyTg(tg, Tools.convertLayout(tg.text)); done()
    }

    private fun toolClean() {
        val tg = getTg(null) ?: return toast("اكتب نصاً أو حدّده أولاً")
        applyTg(tg, Tools.clean(tg.text)); done()
    }

    private fun toolArabizi() {
        val tg = getTg(null) ?: return toast("اكتب مثل: mar7aba ثم اضغط")
        applyTg(tg, Tools.arabizi(tg.text)); done()
    }

    private fun applyStyle(f: (String) -> String) {
        val tg = getTg(null) ?: return toast("اكتب نصاً أو حدّده أولاً")
        applyTg(tg, f(tg.text)); done()
    }

    private fun toolCalc() {
        var tg = getTg(calcRe) ?: return toast("اكتب عملية مثل 25*4+10")
        if (tg.delB > 0 && tg.delA == 0) {
            val lead = tg.text.indexOfFirst { it.isDigit() || it == '(' || it == '-' || it == '.' }
            if (lead < 0) return toast("اكتب عملية مثل 25*4+10")
            val t2 = tg.text.substring(lead)
            tg = Tg(t2, t2.length, 0)
        }
        val r = Tools.calc(tg.text) ?: return toast("اكتب عملية صحيحة مثل 25*4+10")
        applyTg(tg, r); done()
    }

    private fun toolTafqeet() {
        val tg = getTg(numRe) ?: return toast("اكتب رقماً أو حدّده أولاً")
        val n = Tools.normalizeDigits(tg.text).trim().toLongOrNull() ?: return toast("الرقم غير صالح")
        val w = Tools.tafqeet(n) ?: return toast("الرقم كبير جداً")
        applyTg(tg, "${tg.text} ($w)"); done()
    }

    private fun toolUndo() {
        val o = undoOrig
        val n = undoNew
        val ic = currentInputConnection
        if (o == null || n == null || ic == null) { toast("لا يوجد شيء للتراجع عنه"); return }
        ic.beginBatchEdit()
        ic.deleteSurroundingText(n.length, 0)
        ic.commitText(o, 1)
        ic.endBatchEdit()
        undoOrig = null; undoNew = null
        done()
    }

    private fun insertText(s: String) { commit(s); done() }

    private fun dateItems(): List<String> {
        val dayNames = listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")
        val gM = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
        val hM = listOf("محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة")
        val ld = LocalDate.now()
        val g = "${dayNames[ld.dayOfWeek.value - 1]} ${ld.dayOfMonth} ${gM[ld.monthValue - 1]} ${ld.year}م"
        val out = mutableListOf(g)
        try {
            val hd = HijrahDate.from(ld)
            val h = "${hd.get(ChronoField.DAY_OF_MONTH)} ${hM[hd.get(ChronoField.MONTH_OF_YEAR) - 1]} ${hd.get(ChronoField.YEAR)}هـ"
            out.add(0, h)
            out.add("$g  |  $h")
        } catch (e: Throwable) {}
        val tm = LocalTime.now()
        val h12 = if (tm.hour % 12 == 0) 12 else tm.hour % 12
        out.add(String.format("%d:%02d %s", h12, tm.minute, if (tm.hour < 12) "ص" else "م"))
        out.add(ld.toString())
        out.add(ld.toString() + " " + String.format("%02d:%02d", tm.hour, tm.minute))
        return out
    }

    private fun header(h: Int, title: String, back: () -> Unit, extra: View? = null): View {
        val t = th
        val v = mutableListOf<View>(
            key("⬅", 1.2f, 1) { back() },
            TextView(this).apply {
                text = title; setTextColor(t.tx); gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_PX, curH * 0.30f)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, if (extra == null) 4.8f else 2.6f)
            })
        if (extra != null) v.add(extra)
        return row(h, v)
    }

    private fun toolsPanel(h: Int) {
        root.addView(header(h, "⚡ أدوات نوفا", { mode = letterMode; render() }))
        val incog = prefs.getBoolean("incog", false)
        val tiles = listOf<Triple<String, Boolean, () -> Unit>>(
            Triple("🔄\nعكس اللغة", false) { toolConvert() },
            Triple("🧮\nحاسبة", false) { toolCalc() },
            Triple("🔢\nتفقيط", false) { toolTafqeet() },
            Triple("✨\nزخرفة", false) { mode = Mode.STYLE; render() },
            Triple("🧹\nتنظيف", false) { toolClean() },
            Triple("🔤\nعربيزي", false) { toolArabizi() },
            Triple("📅\nالتاريخ", false) { mode = Mode.DATE; render() },
            Triple("🎤\nإملاء", false) { done(); startVoice() },
            Triple("🕌\nعبارات ورموز", false) { mode = Mode.PHR; render() },
            Triple("📝\nاختصاراتي", false) { mode = Mode.SNIP; render() },
            Triple("📋\nالحافظة", false) { mode = Mode.CLIP; render() },
            Triple("😜\nكاموجي", false) { mode = Mode.KAO; render() },
            Triple("📑\nنسخ الكل", false) { toolCopyAll(false) },
            Triple("✂\nقص الكل", false) { toolCopyAll(true) },
            Triple("☑\nتحديد الكل", false) { currentInputConnection?.performContextMenuAction(android.R.id.selectAll); done() },
            Triple("🗑\nمسح الكل", false) { toolClear() },
            Triple("↩\nتراجع", false) { toolUndo() },
            Triple("١٢٣\nأرقام ↔", false) { toolDigits() },
            Triple("Aa\nحالة الأحرف", false) { toolCase() },
            Triple("📊\nعدّاد", false) { toolCount() },
            Triple("🔐\nكلمة سر", false) { toolPassword() },
            Triple(if (incog) "🕶\nتخفّي ✓" else "🕶\nتخفّي", incog) { prefs.edit().putBoolean("incog", !incog).apply(); render() }
        )
        val colv = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tiles.chunked(4).forEach { r ->
            val views = r.map { (label, acc, act) -> key(label, 1f, if (acc) 3 else 1, sz = curH * 0.25f) { act() } }.toMutableList()
            for (i in r.size until 4) views.add(spacer(1f))
            colv.addView(row(h, views))
        }
        root.addView(ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h * 3)
            isVerticalScrollBarEnabled = false
            addView(colv)
        })
        root.addView(panelBottom(h))
    }

    private fun cardView(label: String, act: () -> Unit): TextView {
        val t = th
        return TextView(this).apply {
            text = label; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
            setTextColor(t.fg); setTextSize(TypedValue.COMPLEX_UNIT_PX, curH * 0.30f)
            setPadding(dp(14), 0, dp(14), 0); gravity = Gravity.CENTER_VERTICAL
            background = rounded(t.key)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(dp(2), dp(3), dp(2), dp(3))
            }
            setOnClickListener { feedback(it); act() }
        }
    }

    private fun choicePanel(h: Int, title: String, items: List<Pair<String, () -> Unit>>) {
        root.addView(header(h, title, { mode = Mode.TOOLS; render() }))
        val colv = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        items.forEach { (label, act) -> colv.addView(row(h, listOf(cardView(label, act)))) }
        root.addView(ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h * 3)
            isVerticalScrollBarEnabled = false
            addView(colv)
        })
        root.addView(panelBottom(h))
    }

    private fun snipPanel(h: Int) {
        val t = th
        val l = loadSnips()
        root.addView(header(h, "📝 اختصاراتي · ${l.size}", { mode = Mode.TOOLS; render() }, key("➕ إضافة", 2.2f, 2) { addSnip() }))
        val colv = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (l.isEmpty()) {
            colv.addView(TextView(this).apply {
                text = "احفظ هنا رقمك أو إيميلك أو عنوانك أو الآيبان\nحدّد النص أو انسخه ثم اضغط ➕ إضافة"
                setTextColor(t.tx); gravity = Gravity.CENTER; alpha = 0.7f
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h * 3)
            })
        }
        l.forEach { txt ->
            colv.addView(row(h, listOf(
                cardView(txt.replace("\n", " ")) { commit(txt); done() },
                key("✕", 0.8f, 1, onUp = true, sz = curH * 0.32f) { saveSnips(loadSnips().filter { it != txt }); render() }
            )))
        }
        root.addView(ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h * 3)
            isVerticalScrollBarEnabled = false
            addView(colv)
        })
        root.addView(panelBottom(h))
    }

    private fun loadSnips(): MutableList<String> {
        val out = mutableListOf<String>()
        try {
            val a = JSONArray(prefs.getString("snips", "[]"))
            for (i in 0 until a.length()) out.add(a.getString(i))
        } catch (e: Exception) {}
        return out
    }

    private fun saveSnips(l: List<String>) {
        val a = JSONArray()
        l.forEach { a.put(it) }
        prefs.edit().putString("snips", a.toString()).apply()
    }

    private fun addSnip() {
        val sel = currentInputConnection?.getSelectedText(0)?.toString()
        val s = if (!sel.isNullOrBlank()) sel else loadClips().firstOrNull()?.first
        if (s.isNullOrBlank()) { toast("حدّد نصاً أو انسخه ثم اضغط إضافة"); return }
        val l = loadSnips()
        if (!l.contains(s)) { l.add(0, s); saveSnips(l) }
        toast("تمت الإضافة")
        render()
    }

    // ---------- voice ----------
    private fun startVoice() {
        val lang = if (letterMode == Mode.AR) "ar-SA" else "en-US"
        try {
            startActivity(Intent(this, VoiceActivity::class.java).putExtra("lang", lang)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        } catch (e: Exception) { toast("الإملاء الصوتي غير متاح") }
    }

    fun postFlush() { handler.postDelayed({ flushVoice() }, 600) }

    private fun flushVoice() {
        val t = pendingVoice ?: return
        if (currentInputConnection == null) return
        pendingVoice = null
        commit("$t ")
    }

    // ---------- v4 extras ----------
    private var prevPw: PopupWindow? = null
    private var prevTv: TextView? = null

    private fun showPreview(anchor: View, label: String) {
        if (!prefs.getBoolean("preview", true)) return
        try {
            val t = th
            val w = (anchor.width * 1.5f).toInt()
            val hh = (anchor.height * 1.25f).toInt()
            val loc = IntArray(2)
            anchor.getLocationOnScreen(loc)
            val x = (loc[0] + anchor.width / 2 - w / 2).coerceIn(0, maxOf(0, resources.displayMetrics.widthPixels - w))
            val y = loc[1] - hh - dp(6)
            val pw = prevPw
            val tv = prevTv
            if (pw != null && pw.isShowing && tv != null) {
                tv.text = label
                pw.update(x, y, w, hh)
            } else {
                val n = TextView(this)
                n.gravity = Gravity.CENTER
                n.text = label
                n.setTextColor(t.fg)
                n.setTextSize(TypedValue.COMPLEX_UNIT_PX, hh * 0.5f)
                n.background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(t.sp); setStroke(dp(1), t.acc) }
                val np = PopupWindow(n, w, hh)
                np.isTouchable = false
                np.isFocusable = false
                np.isClippingEnabled = false
                np.showAtLocation(root, Gravity.NO_GRAVITY, x, y)
                prevPw = np; prevTv = n
            }
        } catch (e: Throwable) {}
    }

    private fun dismissPreview() {
        try { prevPw?.dismiss() } catch (e: Throwable) {}
        prevPw = null; prevTv = null
    }

    private fun toolCopyAll(cut: Boolean) {
        val tg = getTg(null) ?: return toast("لا يوجد نص")
        cm.setPrimaryClip(ClipData.newPlainText("nova", tg.text))
        if (cut) { applyTg(tg, ""); toast("تم القص — ↩ للتراجع") } else toast("تم النسخ")
    }

    private fun toolClear() {
        val tg = getTg(null) ?: return toast("لا يوجد نص")
        applyTg(tg, "")
        toast("تم المسح — اضغط ↩ للتراجع")
    }

    private fun toolDigits() {
        val tg = getTg(null) ?: return toast("اكتب أرقاماً أو حدّدها")
        val t = tg.text
        val out = if (t.any { it in '0'..'9' })
            t.map { if (it in '0'..'9') '٠' + (it - '0') else it }.joinToString("")
        else
            t.map { if (it in '٠'..'٩') '0' + (it - '٠') else it }.joinToString("")
        applyTg(tg, out); done()
    }

    private fun toolCase() {
        val tg = getTg(null) ?: return toast("اكتب نصاً أو حدّده")
        val t = tg.text
        val up = t.uppercase()
        val lo = t.lowercase()
        val ti = lo.split(" ").joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }
        applyTg(tg, when { t == lo -> ti; t == ti -> up; t == up -> lo; else -> lo }); done()
    }

    private fun toolCount() {
        val tg = getTg(null) ?: return toast("لا يوجد نص")
        val t = tg.text
        val words = t.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        toast("كلمات: $words  •  حروف: ${t.length}  •  بدون مسافات: ${t.count { !it.isWhitespace() }}")
    }

    private fun toolPassword() {
        val sets = listOf("abcdefghijkmnopqrstuvwxyz", "ABCDEFGHJKLMNPQRSTUVWXYZ", "23456789", "!@#\$%^&*-_?")
        val r = SecureRandom()
        val all = sets.joinToString("")
        val chars = mutableListOf<Char>()
        sets.forEach { chars.add(it[r.nextInt(it.length)]) }
        while (chars.size < 16) chars.add(all[r.nextInt(all.length)])
        chars.shuffle(r)
        val pw = chars.joinToString("")
        commit(pw)
        undoOrig = ""; undoNew = pw
        toast("تم إدراج كلمة سر قوية")
        done()
    }

    private fun phraseItems(): List<Pair<String, String>> = listOf(
        "﷽  بسم الله الرحمن الرحيم" to "﷽",
        "ﷺ  صلى الله عليه وسلم" to "ﷺ",
        "ﷻ  جلّ جلاله" to "ﷻ",
        "﴿ ﴾  قوسا الآيات" to "﴿ ﴾",
        "السلام عليكم ورحمة الله وبركاته" to "السلام عليكم ورحمة الله وبركاته",
        "وعليكم السلام ورحمة الله وبركاته" to "وعليكم السلام ورحمة الله وبركاته",
        "جزاك الله خيراً" to "جزاك الله خيراً",
        "بارك الله فيك" to "بارك الله فيك",
        "ما شاء الله تبارك الله" to "ما شاء الله تبارك الله",
        "إن شاء الله" to "إن شاء الله",
        "الحمد لله" to "الحمد لله",
        "سبحان الله وبحمده" to "سبحان الله وبحمده",
        "لا حول ولا قوة إلا بالله" to "لا حول ولا قوة إلا بالله",
        "أستغفر الله العظيم وأتوب إليه" to "أستغفر الله العظيم وأتوب إليه",
        "صباح الخير" to "صباح الخير",
        "مساء الخير" to "مساء الخير",
        "تصبح على خير" to "تصبح على خير",
        "كل عام وأنتم بخير" to "كل عام وأنتم بخير",
        "عيد مبارك، تقبل الله منا ومنكم" to "عيد مبارك، تقبل الله منا ومنكم",
        "رمضان كريم" to "رمضان كريم",
        "مبروك! ألف مبروك 🎉" to "مبروك! ألف مبروك 🎉",
        "شكراً جزيلاً" to "شكراً جزيلاً",
        "عفواً" to "عفواً",
        "أعتذر عن التأخير" to "أعتذر عن التأخير",
        "أنا في الطريق" to "أنا في الطريق",
        "دقيقة وأرجع لك" to "دقيقة وأرجع لك",
        "في أمان الله" to "في أمان الله"
    )

    private fun kaoItems(): List<String> = listOf(
        "¯\\_(ツ)_/¯", "(╯°□°)╯︵ ┻━┻", "┬─┬ノ( º _ ºノ)", "( ͡° ͜ʖ ͡°)", "(ಥ﹏ಥ)", "(づ｡◕‿‿◕｡)づ",
        "ʕ•ᴥ•ʔ", "(≧◡≦)", "o(≧▽≦)o", "٩(◕‿◕)۶", "(｡•́︿•̀｡)", "♥‿♥", "ಠ_ಠ", "(ง'̀-'́)ง", "(⌐■_■)", "(¬_¬)", "(^_^)v"
    )

    // ---------- colors / rainbow ----------
    private class RKey(val v: TextView, val face: GradientDrawable, val shadow: GradientDrawable, val style: Int) {
        var nx = 0f
        var ny = 0f
    }
    private class Rp(val x: Float, val y: Float, val color: Int, val t0: Long)

    private val rkeys = mutableListOf<RKey>()
    private val ripples = mutableListOf<Rp>()
    private var posReady = false
    private var animating = false
    private val rbStart = SystemClock.uptimeMillis()
    private var fadeStart = 0L
    private class Pal(val name: String, val bg: Int, val key: Int, val sp: Int, val fg: Int, val colors: IntArray)

    private val pals = listOf(
        Pal("Dracula", col(0xFF282A36), col(0xFF343746), col(0xFF44475A), col(0xFFF8F8F2),
            intArrayOf(col(0xFFFF79C6), col(0xFFBD93F9), col(0xFF8BE9FD), col(0xFF50FA7B), col(0xFFF1FA8C), col(0xFFFFB86C), col(0xFFFF5555))),
        Pal("Tokyo Night", col(0xFF1A1B26), col(0xFF24283B), col(0xFF363B58), col(0xFFC0CAF5),
            intArrayOf(col(0xFFF7768E), col(0xFFFF9E64), col(0xFFE0AF68), col(0xFF9ECE6A), col(0xFF7DCFFF), col(0xFF7AA2F7), col(0xFFBB9AF7))),
        Pal("One Dark", col(0xFF21252B), col(0xFF2C313A), col(0xFF3E4451), col(0xFFABB2BF),
            intArrayOf(col(0xFFE06C75), col(0xFFD19A66), col(0xFFE5C07B), col(0xFF98C379), col(0xFF56B6C2), col(0xFF61AFEF), col(0xFFC678DD))),
        Pal("Monokai", col(0xFF1E1F1C), col(0xFF2D2E27), col(0xFF3E3D32), col(0xFFF8F8F2),
            intArrayOf(col(0xFFF92672), col(0xFFFD971F), col(0xFFE6DB74), col(0xFFA6E22E), col(0xFF66D9EF), col(0xFFAE81FF))),
        Pal("Catppuccin", col(0xFF1E1E2E), col(0xFF313244), col(0xFF45475A), col(0xFFCDD6F4),
            intArrayOf(col(0xFFF38BA8), col(0xFFFAB387), col(0xFFF9E2AF), col(0xFFA6E3A1), col(0xFF94E2D5), col(0xFF89B4FA), col(0xFFCBA6F7))),
        Pal("Nord", col(0xFF2E3440), col(0xFF3B4252), col(0xFF4C566A), col(0xFFECEFF4),
            intArrayOf(col(0xFFBF616A), col(0xFFD08770), col(0xFFEBCB8B), col(0xFFA3BE8C), col(0xFF88C0D0), col(0xFF81A1C1), col(0xFFB48EAD))),
        Pal("Matrix", col(0xFF000000), col(0xFF08130B), col(0xFF10251A), col(0xFFB6FFC8),
            intArrayOf(col(0xFF00FF41), col(0xFF39FF14), col(0xFF00CC66), col(0xFF7CFC00), col(0xFF00FF9C)))
    )

    private fun pal(): Pal = pals[prefs.getInt("rbpal", 0).coerceIn(0, pals.size - 1)]
    private fun rainbowThNow(): Th { val p = pal(); return Th(p.bg, p.key, p.sp, p.fg) }

    private fun paletteAt(c: IntArray, pos: Float): Int {
        val x = ((pos % 1f) + 1f) % 1f * c.size
        val i = x.toInt().coerceIn(0, c.size - 1)
        return blend(c[i], c[(i + 1) % c.size], x - i)
    }

    private val rbRun = object : Runnable {
        override fun run() {
            if (!animating) return
            tick()
            handler.postDelayed(this, 33)
        }
    }

    private fun rainbowOn() = prefs.getBoolean("rainbow", false)

    private fun customTh(): Th {
        val c = Color.HSVToColor(floatArrayOf(prefs.getFloat("chue", 210f), prefs.getFloat("csat", 0.6f), prefs.getFloat("cval", 0.8f)))
        val lum = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        return if (lum > 0.6f) Th(blend(c, black, 0.30f), c, blend(c, black, 0.18f), 0xFF111111.toInt())
        else Th(blend(c, black, 0.72f), c, blend(c, white, 0.28f), white)
    }

    private fun startAnim() { if (!animating) { animating = true; handler.post(rbRun) } }
    private fun stopAnim() { animating = false; handler.removeCallbacks(rbRun) }

    private fun baseColor(style: Int, t: Th) = when (style) { 1 -> t.sp; 2, 3 -> t.acc; else -> t.key }

    private fun computePositions() {
        val rl = IntArray(2)
        root.getLocationOnScreen(rl)
        val w = root.width.toFloat().coerceAtLeast(1f)
        val loc = IntArray(2)
        for (k in rkeys) {
            k.v.getLocationOnScreen(loc)
            k.nx = (loc[0] - rl[0] + k.v.width / 2f) / w
            k.ny = (loc[1] - rl[1] + k.v.height / 2f) / w
        }
        posReady = true
    }

    private fun startRipple(v: View) {
        try {
            val loc = IntArray(2)
            v.getLocationOnScreen(loc)
            val rl = IntArray(2)
            root.getLocationOnScreen(rl)
            val w = root.width.toFloat().coerceAtLeast(1f)
            ripples.add(Rp((loc[0] - rl[0] + v.width / 2f) / w, (loc[1] - rl[1] + v.height / 2f) / w,
                pal().colors.let { it[(Math.random() * it.size).toInt().coerceAtMost(it.size - 1)] }, SystemClock.uptimeMillis()))
            if (ripples.size > 6) ripples.removeAt(0)
        } catch (e: Throwable) {}
    }

    private fun tick() {
        if (!::root.isInitialized) return
        if (!posReady) {
            if (rkeys.isEmpty() || rkeys.any { it.v.width == 0 }) return
            computePositions()
        }
        val now = SystemClock.uptimeMillis()
        ripples.removeAll { now - it.t0 > 1100L }
        val rb = rainbowOn()
        if (!rb && ripples.isEmpty()) { stopAnim(); return }
        val t = th
        val p = pal()
        val period = longArrayOf(14000L, 8000L, 4000L)[prefs.getInt("rbspeed", 1).coerceIn(0, 2)]
        val phase = ((now - rbStart) % period).toFloat() / period
        val fade = ((now - fadeStart) / 900f).coerceIn(0f, 1f)
        val black = 0xFF000000.toInt()
        for (k in rkeys) {
            val base = baseColor(k.style, t)
            var face = base
            var edge = blend(base, black, 0.45f)
            var txt = t.fg
            if (rb) {
                val wc = paletteAt(p.colors, phase + k.nx * 0.55f + k.ny * 0.35f)
                val kk = when (k.style) { 0 -> 0.16f; 1 -> 0.26f; else -> 0.36f } * fade
                face = blend(base, wc, kk)
                edge = blend(edge, wc, 0.85f * fade)
                txt = blend(t.fg, wc, 0.9f * fade)
            }
            for (r in ripples) {
                val age = (now - r.t0) / 1100f
                val d = hypot(k.nx - r.x, k.ny - r.y)
                val front = age * 1.25f
                val inner = ((front - d + 0.12f) / 0.25f).coerceIn(0f, 1f)
                val dist = (1f - d / 0.85f).coerceIn(0f, 1f)
                val amp = inner * Math.pow(dist.toDouble(), 1.3).toFloat() * Math.pow((1f - age).toDouble().coerceAtLeast(0.0), 1.5).toFloat()
                if (amp > 0.01f) {
                    face = blend(face, r.color, amp * 0.92f)
                    edge = blend(edge, r.color, amp)
                    txt = blend(txt, t.bg, amp * 0.9f)
                }
            }
            k.face.setColor(face)
            k.shadow.setColor(edge)
            k.v.setTextColor(txt)
        }
    }

    private fun applyThemeLive() {
        val t = th
        outer.setBackgroundColor(t.bg)
        val black = 0xFF000000.toInt()
        for (k in rkeys) {
            val c = baseColor(k.style, t)
            k.face.setColor(c)
            k.shadow.setColor(blend(c, black, 0.45f))
            k.v.setTextColor(t.fg)
        }
    }

    private fun saveCustom(hh: Float?, ss: Float?, vv: Float?) {
        val e = prefs.edit().putBoolean("custom", true).putBoolean("rainbow", false)
        if (hh != null) e.putFloat("chue", hh)
        if (ss != null) e.putFloat("csat", ss)
        if (vv != null) e.putFloat("cval", vv)
        e.apply()
        stopAnim(); ripples.clear()
        applyThemeLive()
    }

    private fun toggleRainbow() {
        val on = !rainbowOn()
        prefs.edit().putBoolean("rainbow", on).apply()
        ripples.clear()
        if (on) fadeStart = SystemClock.uptimeMillis()
        render()
    }

    private fun colorPanel(h: Int) {
        val rb = rainbowOn()
        root.addView(header(h, "🎨 ألوان الكيبورد", { mode = letterMode; render() },
            key(if (rb) "💻 Dev ✓" else "💻 Dev Rainbow", 2.4f, if (rb) 3 else 1) { toggleRainbow() }))
        val size = h * 3
        val h0 = prefs.getFloat("chue", 210f)
        val s0 = prefs.getFloat("csat", 0.6f)
        val v0 = prefs.getFloat("cval", 0.8f)
        val bar = ValueBarView(this).apply {
            baseHue = h0; baseSat = s0; value = v0
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(dp(3), dp(8), dp(3), dp(8))
            }
            onPick = { vv -> saveCustom(null, null, vv) }
            onDone = { render() }
        }
        val wheel = ColorWheelView(this).apply {
            hue = h0; sat = s0
            layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(dp(4), 0, dp(8), 0) }
            onPick = { hh, ss -> bar.baseHue = hh; bar.baseSat = ss; bar.invalidate(); saveCustom(hh, ss, null) }
            onDone = { render() }
        }
        val ripple = prefs.getBoolean("ripple", true)
        val names = listOf("بطيئة", "متوسطة", "سريعة")
        val spd = prefs.getInt("rbspeed", 1).coerceIn(0, 2)
        val pi = prefs.getInt("rbpal", 0).coerceIn(0, pals.size - 1)
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, size, 1f)
            addView(row(h, listOf<View>(bar)))
            addView(row(h, listOf(
                key(if (ripple) "💥 توهج ✓" else "💥 توهج", 1f, if (ripple) 3 else 1) {
                    prefs.edit().putBoolean("ripple", !ripple).apply(); render()
                },
                key("⚡ ${names[spd]}", 1f, 1) {
                    prefs.edit().putInt("rbspeed", (spd + 1) % 3).apply(); render()
                })))
            addView(row(h, listOf(key("🧑‍💻 ${pals[pi].name}", 1f, 1) {
                prefs.edit().putInt("rbpal", (pi + 1) % pals.size).apply(); render()
            })))
        }
        root.addView(row(size, listOf<View>(wheel, right)))
        root.addView(panelBottom(h))
    }
}
