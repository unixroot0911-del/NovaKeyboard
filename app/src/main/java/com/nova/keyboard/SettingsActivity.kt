package com.nova.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.*

class SettingsActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = getSharedPreferences("nova", MODE_PRIVATE)
        val d = resources.displayMetrics.density
        val l = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((24 * d).toInt(), (48 * d).toInt(), (24 * d).toInt(), 24 * d.toInt())
        }
        fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }.also { l.addView(it) }
        l.addView(TextView(this).apply { text = "Nova Keyboard / لوحة نوفا"; textSize = 24f; gravity = Gravity.CENTER })
        btn("1) تفعيل الكيبورد") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        btn("2) اختيار الكيبورد") { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
        l.addView(Switch(this).apply {
            text = "الاهتزاز"; isChecked = p.getBoolean("haptic", true)
            setOnCheckedChangeListener { _, c -> p.edit().putBoolean("haptic", c).apply() }
        })
        l.addView(Switch(this).apply {
            text = "الوضع الداكن"; isChecked = p.getBoolean("dark", true)
            setOnCheckedChangeListener { _, c -> p.edit().putBoolean("dark", c).apply() }
        })
        l.addView(EditText(this).apply { hint = "جرّب الكتابة هنا" })
        setContentView(ScrollView(this).apply { addView(l) })
    }
}
