package com.nova.keyboard

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

object Tools {
    // ---- عكس اللغة (لوحة مفاتيح خاطئة) ----
    private val enAr = mapOf(
        'q' to "ض", 'w' to "ص", 'e' to "ث", 'r' to "ق", 't' to "ف", 'y' to "غ", 'u' to "ع", 'i' to "ه",
        'o' to "خ", 'p' to "ح", '[' to "ج", ']' to "د", 'a' to "ش", 's' to "س", 'd' to "ي", 'f' to "ب",
        'g' to "ل", 'h' to "ا", 'j' to "ت", 'k' to "ن", 'l' to "م", ';' to "ك", '\'' to "ط", 'z' to "ئ",
        'x' to "ء", 'c' to "ؤ", 'v' to "ر", 'b' to "لا", 'n' to "ى", 'm' to "ة", ',' to "و", '.' to "ز",
        '/' to "ظ", '`' to "ذ")
    private val arEn: Map<Char, Char> by lazy {
        val m = HashMap<Char, Char>()
        for ((k, v) in enAr) if (v.length == 1) m[v[0]] = k
        m
    }

    fun convertLayout(s: String): String {
        var ar = 0
        var lat = 0
        for (c in s) { if (c in '\u0600'..'\u06FF') ar++ else if (c.isLetter()) lat++ }
        val sb = StringBuilder()
        if (ar >= lat) {
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == 'ل' && i + 1 < s.length && s[i + 1] == 'ا') { sb.append('b'); i += 2; continue }
                sb.append(arEn[c] ?: c)
                i++
            }
        } else {
            for (c in s) sb.append(enAr[c.lowercaseChar()] ?: c.toString())
        }
        return sb.toString()
    }

    // ---- تطويل ----
    private const val NO_CONNECT = "ادذرزوؤأإآءةى"
    fun kashida(s: String): String {
        val sb = StringBuilder()
        for (i in s.indices) {
            val c = s[i]
            sb.append(c)
            if (c in '\u0621'..'\u064A' && NO_CONNECT.indexOf(c) < 0 && i + 1 < s.length && s[i + 1] in '\u0621'..'\u064A')
                sb.append('ـ')
        }
        return sb.toString()
    }

    // ---- تنظيف ----
    fun clean(s: String): String =
        s.replace(Regex("[\u064B-\u065F\u0670\u0640]"), "").replace(Regex("[ \t]+"), " ").trim()

    // ---- عربيزي -> عربي ----
    private val digraph = mapOf(
        "sh" to "ش", "kh" to "خ", "gh" to "غ", "th" to "ث", "dh" to "ذ", "aa" to "ا",
        "ee" to "ي", "ii" to "ي", "oo" to "و", "uu" to "و", "ou" to "و")
    private val single = mapOf(
        'b' to "ب", 't' to "ت", 'j' to "ج", 'd' to "د", 'r' to "ر", 'z' to "ز", 's' to "س", 'f' to "ف",
        'q' to "ق", 'k' to "ك", 'l' to "ل", 'm' to "م", 'n' to "ن", 'h' to "ه", 'w' to "و", 'y' to "ي",
        'g' to "ج", 'p' to "ب", 'v' to "ف", 'x' to "كس", 'c' to "ك", '2' to "ء", '3' to "ع", '5' to "خ",
        '6' to "ط", '7' to "ح", '8' to "غ", '9' to "ق")

    fun arabizi(s: String): String {
        val sb = StringBuilder()
        var last = 0
        for (m in Regex("[A-Za-z0-9']+").findAll(s)) {
            sb.append(s, last, m.range.first)
            sb.append(arabiziWord(m.value))
            last = m.range.last + 1
        }
        sb.append(s, last, s.length)
        return sb.toString()
    }

    private fun arabiziWord(w: String): String {
        if (w.all { it.isDigit() }) return w
        val t = w.lowercase()
        val sb = StringBuilder()
        var i = 0
        while (i < t.length) {
            val c = t[i]
            if (c == '\'') { i++; continue }
            if (c in "3769" && i + 1 < t.length && t[i + 1] == '\'') {
                sb.append(when (c) { '3' -> "غ"; '7' -> "خ"; '6' -> "ظ"; else -> "ض" })
                i += 2; continue
            }
            if (i + 1 < t.length) {
                val v = digraph[t.substring(i, i + 2)]
                if (v != null) { sb.append(v); i += 2; continue }
            }
            if (c in "aeiou") {
                val atStart = i == 0
                val atEnd = i == t.length - 1
                sb.append(when {
                    atStart -> if (c in "aei") "ا" else "او"
                    atEnd -> when (c) { 'a' -> "ا"; 'e', 'i' -> "ي"; else -> "و" }
                    else -> ""
                })
                i++; continue
            }
            sb.append(single[c] ?: c.toString())
            i++
        }
        return sb.toString()
    }

    // ---- أنماط الخط والزخرفة ----
    private fun mapLatin(s: String, up: Int, low: Int, dig: Int): String {
        val sb = StringBuilder()
        for (ch in s) {
            when {
                ch in 'A'..'Z' && up >= 0 -> sb.appendCodePoint(up + (ch - 'A'))
                ch in 'a'..'z' && low >= 0 -> sb.appendCodePoint(low + (ch - 'a'))
                ch in '0'..'9' && dig >= 0 -> sb.appendCodePoint(dig + (ch - '0'))
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private val dsCaps = mapOf('C' to 0x2102, 'H' to 0x210D, 'N' to 0x2115, 'P' to 0x2119,
        'Q' to 0x211A, 'R' to 0x211D, 'Z' to 0x2124)

    private fun doubleStruck(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            when {
                ch in 'A'..'Z' -> sb.appendCodePoint(dsCaps[ch] ?: (0x1D538 + (ch - 'A')))
                ch in 'a'..'z' -> sb.appendCodePoint(0x1D552 + (ch - 'a'))
                ch in '0'..'9' -> sb.appendCodePoint(0x1D7D8 + (ch - '0'))
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun circled(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            when {
                ch in 'A'..'Z' -> sb.appendCodePoint(0x24B6 + (ch - 'A'))
                ch in 'a'..'z' -> sb.appendCodePoint(0x24D0 + (ch - 'a'))
                ch == '0' -> sb.appendCodePoint(0x24EA)
                ch in '1'..'9' -> sb.appendCodePoint(0x2460 + (ch - '1'))
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun combine(s: String, mark: Char) =
        s.map { if (it.isWhitespace()) it.toString() else "$it$mark" }.joinToString("")

    val styles: List<Pair<String, (String) -> String>> = listOf(
        "تطويل: مـرحـبـا" to { s: String -> kashida(s) },
        "𝗕𝗼𝗹𝗱 Bold" to { s: String -> mapLatin(s, 0x1D5D4, 0x1D5EE, 0x1D7EC) },
        "𝘐𝘵𝘢𝘭𝘪𝘤 Italic" to { s: String -> mapLatin(s, 0x1D608, 0x1D622, -1) },
        "𝓢𝓬𝓻𝓲𝓹𝓽 Script" to { s: String -> mapLatin(s, 0x1D4D0, 0x1D4EA, -1) },
        "𝕕𝕠𝕦𝕓𝕝𝕖 Double" to { s: String -> doubleStruck(s) },
        "𝙼𝚘𝚗𝚘 Mono" to { s: String -> mapLatin(s, 0x1D670, 0x1D68A, 0x1D7F6) },
        "ⓒⓘⓡⓒⓛⓔ Circle" to { s: String -> circled(s) },
        "Ｗｉｄｅ Wide" to { s: String -> mapLatin(s, 0xFF21, 0xFF41, 0xFF10) },
        "S̶t̶r̶i̶k̶e̶ مشطوب" to { s: String -> combine(s, '\u0336') },
        "U̲n̲d̲e̲r̲l̲i̲n̲e̲ تحته خط" to { s: String -> combine(s, '\u0332') },
        "﴿ زخرفة قرآنية ﴾" to { s: String -> "﴿ $s ﴾" },
        "★ نجوم ★" to { s: String -> "★ $s ★" },
        "꧁ إطار ꧂" to { s: String -> "꧁$s꧂" },
        "『 إطار ياباني 』" to { s: String -> "『$s』" },
        "✦ لمعة ✦" to { s: String -> "✦ $s ✦" },
        "• • نقاط • •" to { s: String -> "• • $s • •" }
    )

    // ---- حاسبة ----
    private class P(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i] == ' ') i++ }
        fun expr(): Double {
            var v = term()
            while (true) {
                ws()
                if (i < s.length && (s[i] == '+' || s[i] == '-')) {
                    val op = s[i++]
                    val r = term()
                    v = if (op == '+') v + r else v - r
                } else return v
            }
        }
        fun term(): Double {
            var v = pow()
            while (true) {
                ws()
                if (i < s.length && (s[i] == '*' || s[i] == '/')) {
                    val op = s[i++]
                    val r = pow()
                    v = if (op == '*') v * r else v / r
                } else return v
            }
        }
        fun pow(): Double {
            val b = unary()
            ws()
            if (i < s.length && s[i] == '^') { i++; return Math.pow(b, pow()) }
            return b
        }
        fun unary(): Double {
            ws()
            if (i < s.length && s[i] == '-') { i++; return -unary() }
            if (i < s.length && s[i] == '+') { i++; return unary() }
            return post()
        }
        fun post(): Double {
            var v = atom()
            ws()
            while (i < s.length && s[i] == '%') { i++; v /= 100.0; ws() }
            return v
        }
        fun atom(): Double {
            ws()
            if (i < s.length && s[i] == '(') {
                i++
                val v = expr()
                ws()
                if (i < s.length && s[i] == ')') i++ else throw IllegalArgumentException()
                return v
            }
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (st == i) throw IllegalArgumentException()
            return s.substring(st, i).toDouble()
        }
    }

    fun normalizeDigits(raw: String): String = raw.map { c ->
        when {
            c in '٠'..'٩' -> ('0' + (c - '٠')).toString()
            c == '٫' -> "."
            c == '٬' || c == ',' -> ""
            else -> c.toString()
        }
    }.joinToString("")

    fun calc(raw: String): String? {
        val s = normalizeDigits(raw).map { c ->
            when (c) { 'x', 'X', '×' -> "*"; '÷' -> "/"; '=' -> ""; else -> c.toString() }
        }.joinToString("").trim()
        if (s.isEmpty() || !s.drop(1).any { it in "+-*/^%" }) return null
        return try {
            val p = P(s)
            val v = p.expr()
            p.ws()
            if (p.i != s.length || v.isNaN() || v.isInfinite()) return null
            if (v == Math.rint(v) && abs(v) < 1e15) v.toLong().toString()
            else BigDecimal(v).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        } catch (e: Exception) { null }
    }

    // ---- تفقيط ----
    private val ones = listOf("", "واحد", "اثنان", "ثلاثة", "أربعة", "خمسة", "ستة", "سبعة", "ثمانية", "تسعة")
    private val teens = listOf("عشرة", "أحد عشر", "اثنا عشر", "ثلاثة عشر", "أربعة عشر", "خمسة عشر",
        "ستة عشر", "سبعة عشر", "ثمانية عشر", "تسعة عشر")
    private val tens = listOf("", "", "عشرون", "ثلاثون", "أربعون", "خمسون", "ستون", "سبعون", "ثمانون", "تسعون")
    private val hund = listOf("", "مائة", "مائتان", "ثلاثمائة", "أربعمائة", "خمسمائة", "ستمائة",
        "سبعمائة", "ثمانمائة", "تسعمائة")

    private fun u999(n: Int): String {
        val parts = mutableListOf<String>()
        val h = n / 100
        val r = n % 100
        if (h > 0) parts.add(hund[h])
        if (r in 1..9) parts.add(ones[r])
        else if (r in 10..19) parts.add(teens[r - 10])
        else if (r >= 20) {
            val o = r % 10
            val t = r / 10
            parts.add(if (o > 0) "${ones[o]} و${tens[t]}" else tens[t])
        }
        return parts.joinToString(" و")
    }

    private fun group(n: Int, s: String, d: String, p: String, acc: String): String {
        if (n == 1) return s
        if (n == 2) return d
        val r = n % 100
        val w = u999(n)
        return when {
            r in 3..10 -> "$w $p"
            r in 11..99 -> "$w $acc"
            else -> "$w $s"
        }
    }

    fun tafqeet(n: Long): String? {
        if (n < 0 || n >= 1_000_000_000_000L) return null
        if (n == 0L) return "صفر"
        val parts = mutableListOf<String>()
        val bil = (n / 1_000_000_000L).toInt()
        val mil = ((n / 1_000_000L) % 1000).toInt()
        val th = ((n / 1000L) % 1000).toInt()
        val rest = (n % 1000).toInt()
        if (bil > 0) parts.add(group(bil, "مليار", "ملياران", "مليارات", "ملياراً"))
        if (mil > 0) parts.add(group(mil, "مليون", "مليونان", "ملايين", "مليوناً"))
        if (th > 0) parts.add(group(th, "ألف", "ألفان", "آلاف", "ألفاً"))
        if (rest > 0) parts.add(u999(rest))
        return parts.joinToString(" و")
    }
}
