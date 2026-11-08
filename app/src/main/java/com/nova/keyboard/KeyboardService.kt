package com.nova.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
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
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

class KeyboardService : InputMethodService() {
    private enum class Mode { AR, EN, SYM, SYM2, EMO, CLIP }
    private class Th(val bg: Int, val key: Int, val sp: Int, val fg: Int, val acc: Int, val accFg: Int)

    private fun col(x: Long) = x.toInt()
    private val themes = listOf(
        Th(col(0xFF1B1B1F), col(0xFF3A3B41), col(0xFF2A2B30), col(0xFFFFFFFF), col(0xFF4C8DFF), col(0xFFFFFFFF)),
        Th(col(0xFFE3E6EA), col(0xFFFFFFFF), col(0xFFC5CAD1), col(0xFF111111), col(0xFF1A73E8), col(0xFFFFFFFF)),
        Th(col(0xFF000000), col(0xFF1C1C1E), col(0xFF2C2C2E), col(0xFFFFFFFF), col(0xFF0A84FF), col(0xFFFFFFFF)),
        Th(col(0xFF0D2538), col(0xFF17405F), col(0xFF0F3047), col(0xFFE8F4FF), col(0xFF29B6F6), col(0xFF00243A)),
        Th(col(0xFF2B1520), col(0xFF4A2535), col(0xFF38202B), col(0xFFFFE9F1), col(0xFFFF4F8B), col(0xFFFFFFFF))
    )

    private var mode = Mode.AR
    private var letterMode = Mode.AR
    private var shift = 0
    private var lastShiftTap = 0L
    private var lastSpace = 0L
    private var emojiCat = 1
    private var curH = 0
    private var repCount = 0
    private lateinit var outer: FrameLayout
    private lateinit var root: LinearLayout
    private lateinit var cm: ClipboardManager
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("nova", MODE_PRIVATE) }
    private val th: Th get() = themes[prefs.getInt("theme", 0).coerceIn(0, themes.size - 1)]

    private var popup: PopupWindow? = null
    private var popAlts: List<String> = emptyList()
    private var popTv: List<TextView> = emptyList()
    private var popSel = 0
    private var popLeft = 0
    private var popItemW = 1

    private val ar = listOf(
        "ض ص ث ق ف غ ع ه خ ح ج د".split(" "),
        "ش س ي ب ل ا ت ن م ك ط ذ".split(" "),
        "ئ ء ؤ ر ى ة و ز ظ".split(" "))
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
        "و" to listOf("ؤ"), "ل" to listOf("لا", "لأ", "لإ", "لآ"), "ك" to listOf("گ"),
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
        cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.addPrimaryClipChangedListener(clipListener)
        letterMode = if (prefs.getInt("lang", 0) == 1) Mode.EN else Mode.AR
        mode = letterMode
    }

    override fun onDestroy() {
        cm.removePrimaryClipChangedListener(clipListener)
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
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        dismissPopup()
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
            else -> keysPanel(h)
        }
    }

    private fun toolbar(h: Int): View {
        val s = curH * 0.30f
        return row((h * 0.8f).toInt(), listOf(
            key("⚙", 1f, 1, sz = s) { openSettings() },
            key("📋", 1f, 1, sz = s) { mode = Mode.CLIP; render() },
            key("🎨", 1f, 1, sz = s) {
                prefs.edit().putInt("theme", (prefs.getInt("theme", 0) + 1) % themes.size).apply(); render()
            },
            key("◀", 1f, 1, repeat = true, sz = s) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT) },
            key("▶", 1f, 1, repeat = true, sz = s) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT) },
            key("🤚", 1f, if (prefs.getInt("onehand", 0) != 0) 2 else 1, sz = s) {
                prefs.edit().putInt("onehand", (prefs.getInt("onehand", 0) + 1) % 3).apply(); render()
            },
            key("⌄", 1f, 1, sz = s) { requestHideSelf(0) }
        ))
    }

    private fun keysPanel(h: Int) {
        if (prefs.getBoolean("numrow", true)) {
            root.addView(row(h, "1234567890".map { d ->
                key("$d", 1f, alts = listOf("٠١٢٣٤٥٦٧٨٩"[d - '0'].toString())) { commit("$d"); afterText() }
            }))
        }
        val rows = when (mode) {
            Mode.AR -> if (shift != 0) arShift else ar
            Mode.EN -> en
            Mode.SYM -> sym
            else -> sym2
        }
        root.addView(row(h, rows[0].map { letterKey(it) }))
        val r2 = mutableListOf<View>()
        if (mode == Mode.EN) r2.add(spacer(0.5f))
        rows[1].forEach { r2.add(letterKey(it)) }
        if (mode == Mode.EN) r2.add(spacer(0.5f))
        root.addView(row(h, r2))
        val r3 = mutableListOf<View>()
        when (mode) {
            Mode.AR -> r3.add(key("◌َ", 1.5f, if (shift != 0) 2 else 1) { onShift() })
            Mode.EN -> r3.add(key(if (shift == 0) "⇧" else if (shift == 1) "⬆" else "⇪", 1.5f, if (shift != 0) 2 else 1) { onShift() })
            Mode.SYM -> r3.add(key("=\\<", 1.5f, 1) { mode = Mode.SYM2; render() })
            else -> r3.add(key("123", 1.5f, 1) { mode = Mode.SYM; render() })
        }
        rows[2].forEach { r3.add(letterKey(it)) }
        r3.add(key("⌫", 1.5f, 1, repeat = true) { backspace() })
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
            key(if (isAr) "العربية" else "English", 4.2f, 0, onLong = { picker() }) { space() },
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
            key(c, 1f, if (i == emojiCat) 2 else 1, sz = curH * 0.38f) { emojiCat = i; render() }
        }))
        val list = if (emojiCat == 0) recents() else emojiData[emojiCat - 1].split(" ").filter { it.isNotEmpty() }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (list.isEmpty()) {
            col.addView(TextView(this).apply {
                text = "لا توجد إيموجي حديثة بعد"; setTextColor(th.fg); gravity = Gravity.CENTER
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
        key("⌫", 2f, 1, repeat = true) { backspace() }
    ))

    private fun clipPanel(h: Int) {
        val t = th
        val clips = loadClips()
        root.addView(row(h, listOf(
            key("⌨", 1.2f, 1) { mode = letterMode; render() },
            TextView(this).apply {
                text = "الحافظة · ${clips.size}"; setTextColor(t.fg); gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_PX, curH * 0.34f)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 3.6f)
            },
            key("🗑 مسح", 2f, 1) { saveClips(loadClips().filter { it.second }); render() }
        )))
        val colv = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (clips.isEmpty()) {
            colv.addView(TextView(this).apply {
                text = "لا يوجد شيء منسوخ بعد\nانسخ أي نص وسيظهر هنا"
                setTextColor(t.fg); gravity = Gravity.CENTER; alpha = 0.7f
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
                key("📌", 0.8f, if (pin) 2 else 1, onUp = true, sz = curH * 0.32f) { togglePin(txt); render() },
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

    private fun rounded(c: Int) = GradientDrawable().apply {
        cornerRadius = dp(8).toFloat(); setColor(c)
        setStroke(1, (th.fg and 0x00FFFFFF) or 0x1F000000)
    }

    private fun key(
        label: String, w: Float, style: Int = 0, repeat: Boolean = false,
        alts: List<String> = emptyList(), onLong: (() -> Unit)? = null,
        onUp: Boolean = false, sz: Float = 0f, onAlt: ((String) -> Unit)? = null,
        onTap: () -> Unit
    ): View {
        val t = th
        val bgc = when (style) { 1 -> t.sp; 2 -> t.acc; else -> t.key }
        val fgc = if (style == 2) t.accFg else t.fg
        val tv = TextView(this)
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.setTextColor(fgc)
        tv.includeFontPadding = false
        val px = if (sz > 0f) sz else if (label.codePointCount(0, label.length) > 2) curH * 0.27f else curH * 0.40f
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
        tv.background = rounded(bgc)
        val mx = maxOf(1, (dp(1) * 1.5f).toInt())
        tv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, w).apply {
            setMargins(mx, dp(3), mx, dp(3))
        }
        val deferred = alts.isNotEmpty() || onLong != null || onUp
        var longFired = false
        var moved = false
        var downX = 0f
        var downY = 0f
        val longRun = Runnable {
            if (alts.isNotEmpty()) { longFired = true; feedback(tv); showPopup(tv, alts) }
            else if (onLong != null) { longFired = true; feedback(tv); onLong() }
        }
        val rep = object : Runnable {
            override fun run() { repCount++; onTap(); handler.postDelayed(this, 45) }
        }
        tv.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    longFired = false; moved = false; downX = e.rawX; downY = e.rawY
                    v.alpha = 0.6f
                    if (!onUp) feedback(v)
                    if (repeat) { repCount = 0; onTap(); handler.postDelayed(rep, 380) }
                    else if (!deferred) onTap()
                    else if (!onUp) handler.postDelayed(longRun, 330)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (popup != null) updatePopupSel(e.rawX)
                    else if (onUp && (abs(e.rawX - downX) > dp(8) || abs(e.rawY - downY) > dp(8))) moved = true
                }
                MotionEvent.ACTION_UP -> {
                    v.alpha = 1f
                    handler.removeCallbacks(rep); handler.removeCallbacks(longRun)
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
                    dismissPopup()
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

    private fun backspace() {
        val ic = currentInputConnection ?: return
        if (repCount > 12) {
            val b = ic.getTextBeforeCursor(60, 0)?.toString()
            if (!b.isNullOrEmpty()) {
                var i = b.length
                while (i > 0 && b[i - 1].isWhitespace()) i--
                while (i > 0 && !b[i - 1].isWhitespace()) i--
                ic.deleteSurroundingText(b.length - i, 0)
                return
            }
        }
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
            val d = cm.primaryClipDescription
            if (d?.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) return
            val t = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
            if (!t.isNullOrBlank() && t.length < 20000) addClip(t)
        } catch (e: Throwable) {}
    }
}
