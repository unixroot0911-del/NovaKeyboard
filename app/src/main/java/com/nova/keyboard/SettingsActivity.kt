package com.nova.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*

class SettingsActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = getSharedPreferences("nova", MODE_PRIVATE)
        val d = resources.displayMetrics.density
        val pad = (20 * d).toInt()
        val l = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, (36 * d).toInt(), pad, pad)
        }
        fun title(t: String) = TextView(this).apply {
            text = t; textSize = 16f; setPadding(0, (16 * d).toInt(), 0, (4 * d).toInt())
        }.also { l.addView(it) }
        fun sw(t: String, k: String, def: Boolean) = Switch(this).apply {
            text = t; isChecked = p.getBoolean(k, def)
            setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
            setOnCheckedChangeListener { _, c -> p.edit().putBoolean(k, c).apply() }
        }.also { l.addView(it) }

        l.addView(TextView(this).apply { text = "Nova Keyboard 2.0"; textSize = 24f })
        l.addView(Button(this).apply {
            text = "1) تفعيل الكيبورد"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        })
        l.addView(Button(this).apply {
            text = "2) اختيار الكيبورد"
            setOnClickListener { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
        })
        l.addView(EditText(this).apply { hint = "جرّب الكتابة هنا" })

        val hl = TextView(this)
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
        l.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("داكن", "فاتح", "أسود AMOLED", "أزرق", "وردي"))
            setSelection(p.getInt("theme", 0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    p.edit().putInt("theme", pos).apply()
                }
                override fun onNothingSelected(a: AdapterView<*>?) {}
            }
        })

        title("الخيارات")
        sw("الاهتزاز", "haptic", true)
        sw("صوت الضغط", "sound", false)
        sw("صف الأرقام", "numrow", true)
        sw("حرف كبير تلقائي (إنجليزي)", "autocap", true)
        sw("مسافتان = نقطة", "dblspace", true)

        title("نصائح")
        l.addView(TextView(this).apply {
            text = "• اضغط مطولاً على أي حرف لرؤية بدائله (أ إ آ، é، ٢...)\n" +
                "• اضغط مطولاً على 🌐 لتبديل الكيبورد\n" +
                "• اضغط مرتين على ⇧ لقفل الحروف الكبيرة\n" +
                "• 📋 يفتح سجل الحافظة (ثبّت 📌 ما تريد)\n" +
                "• 🤚 وضع اليد الواحدة، 🎨 تغيير السمة\n" +
                "• اضغط مطولاً على ⌫ لحذف كلمات كاملة"
        })
        setContentView(ScrollView(this).apply { addView(l) })
    }
}
