package com.nova.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*

class SettingsActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val bg = Color.parseColor("#121212")
        window.decorView.setBackgroundColor(bg)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val p = getSharedPreferences("nova", MODE_PRIVATE)
        val d = resources.displayMetrics.density
        val pad = (20 * d).toInt()
        val l = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(pad, (36 * d).toInt(), pad, pad)
        }
        fun title(t: String) = TextView(this).apply {
            text = t; textSize = 16f; setTextColor(Color.parseColor("#4C8DFF"))
            setPadding(0, (18 * d).toInt(), 0, (4 * d).toInt())
        }.also { l.addView(it) }
        fun sw(t: String, k: String, def: Boolean) = Switch(this).apply {
            text = t; isChecked = p.getBoolean(k, def); setTextColor(Color.WHITE)
            setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
            setOnCheckedChangeListener { _, c -> p.edit().putBoolean(k, c).apply() }
        }.also { l.addView(it) }
        fun spinner(items: List<String>, key: String) = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, items)
            setSelection(p.getInt(key, 0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    p.edit().putInt(key, pos).apply()
                }
                override fun onNothingSelected(a: AdapterView<*>?) {}
            }
        }.also { l.addView(it) }

        l.addView(TextView(this).apply { text = "Nova Keyboard 5.0"; textSize = 26f; setTextColor(Color.WHITE) })
        l.addView(Button(this).apply {
            text = "1) تفعيل الكيبورد"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        })
        l.addView(Button(this).apply {
            text = "2) اختيار الكيبورد"
            setOnClickListener { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
        })
        l.addView(EditText(this).apply {
            hint = "جرّب الكتابة هنا"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
        })

        title("ترتيب الحروف العربية")
        spinner(listOf("قياسي (ويندوز / آيفون / Gboard)", "أبجدي (ا ب ت ث ج…)", "قياسي مع ذ ظاهرة"), "arlayout")

        val hl = TextView(this).apply { setTextColor(Color.WHITE) }
        fun hText(v: Int) { hl.text = "ارتفاع الكيبورد: $v%" }
        title("الحجم")
        l.addView(hl)
        l.addView(SeekBar(this).apply {
            max = 25
            progress = p.getInt("height", 44) - 30
            hText(30 + progress)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, pr: Int, u: Boolean) {
                    p.edit().putInt("height", 30 + pr).apply(); hText(30 + pr)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })

        title("السمة")
        spinner(listOf("داكن", "فاتح", "أسود AMOLED", "أزرق", "وردي"), "theme")

        title("الخيارات")
        sw("معاينة الحرف عند الضغط", "preview", true)
        sw("الاهتزاز", "haptic", true)
        sw("صوت الضغط", "sound", false)
        sw("صف الأرقام", "numrow", true)
        sw("حرف كبير تلقائي (إنجليزي)", "autocap", true)
        sw("مسافتان = نقطة", "dblspace", true)

        title("نصائح")
        l.addView(TextView(this).apply {
            setTextColor(Color.parseColor("#CCCCCC"))
            text = "• اضغط مطولاً على 🎨 لعجلة الألوان وRainbow\n" +
                "• اسحب على المسافة يميناً ويساراً لتحريك المؤشر\n" +
                "• اسحب ⌫ لليسار لحذف كلمات، أو اضغط عليه مطولاً\n" +
                "• اضغط مطولاً على أي حرف لبدائله (أ إ آ، é، ٢…)\n" +
                "• اضغط مطولاً على الأرقام للرموز (! @ # …)\n" +
                "• اضغط مرتين على ⇧ لقفل الحروف الكبيرة\n" +
                "• ⚡ لوحة الأدوات: عكس اللغة، حاسبة، تفقيط، زخرفة، عربيزي، تاريخ هجري، عبارات، اختصاراتي، نسخ/قص الكل، كلمة سر، تخفّي…\n" +
                "• 🎤 إملاء صوتي (يحتاج خدمة Google)"
        })
        setContentView(ScrollView(this).apply { setBackgroundColor(bg); addView(l) })
    }
}
