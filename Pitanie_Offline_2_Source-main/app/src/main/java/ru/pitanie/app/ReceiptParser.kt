package ru.pitanie.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Item(
    val name: String,
    val qty: Double,
    val unit: String,
    val total: Double,
    val kcal100: Double = 0.0
) {
    fun json(): JSONObject = JSONObject()
        .put("name", name).put("qty", qty).put("unit", unit)
        .put("total", total).put("kcal100", kcal100)

    companion object {
        fun from(j: JSONObject) = Item(
            j.optString("name"), j.optDouble("qty", 1.0),
            j.optString("unit", "шт"), j.optDouble("total"),
            j.optDouble("kcal100", 0.0)
        )
    }
}

data class Receipt(
    val date: String,
    val store: String,
    val printedTotal: Double,
    val items: List<Item>
) {
    fun json(): JSONObject = JSONObject()
        .put("date", date).put("store", store)
        .put("printedTotal", printedTotal)
        .put("items", JSONArray().apply { items.forEach { put(it.json()) } })

    companion object {
        fun from(j: JSONObject): Receipt {
            val a = j.optJSONArray("items") ?: JSONArray()
            return Receipt(
                j.optString("date"), j.optString("store"),
                j.optDouble("printedTotal"),
                (0 until a.length()).map { Item.from(a.getJSONObject(it)) }
            )
        }
    }
}

object ReceiptParser {
    private val money = Regex("(?<![\\d.,])\\d{1,5}[.,]\\d{2}(?!\\d)")
    private val tax = Regex("^\\s*[|!]?\\s*(?:НДС|НАС|НОС|НС|HAC|HOC|HДC|НOC)\\s*(?:10|20|22|0|102|107|227)\\s*%?", RegexOption.IGNORE_CASE)
    private val end = Regex("^(?:СКИДК[АИ]\\s+НА|ИТОГ|ИТОГО|К\\s+ОПЛАТЕ|БЕЗНАЛИЧ|НАЛИЧНЫМИ|СУММА\\s+НДС)", RegexOption.IGNORE_CASE)
    private val junk = Regex("^(?:КАССОВ|ЦЕНА|КОЛ[\\s-]*ВО|СКИДК|СУММА|КАССИР|ПРИХОД|ПОДЫТОГ|ОПЛАТА|СДАЧА|ВСЕГО|ЭЛЕКТРОН|КАРТА|ФН\\b|ФД\\b|ФП\\b|ИНН\\b|ККТ\\b|QR\\b|В\\s+ТОМ\\s+ЧИСЛЕ)", RegexOption.IGNORE_CASE)
    private val dateDMY = Regex("\\b(\\d{2})[./-](\\d{2})[./-](20\\d{2})\\b")
    private val dateYMD = Regex("\\b(20\\d{2})[./-](\\d{2})[./-](\\d{2})\\b")
    private val weight = Regex("(?<!\\d)(0[.,]\\d{2,4}|\\d[.,]\\d{3})\\s*(?:кг|kg|кr|Kr)", RegexOption.IGNORE_CASE)
    private val pack = Regex("(?<!\\d)(\\d{1,4}(?:[.,]\\d+)?)\\s*(кг|kg|гр|г|мл|л)\\b", RegexOption.IGNORE_CASE)
    private val pieces = Regex("(?<!\\d)(\\d{1,3})\\s*(?:шт|шt|ш\\.|шт\\.)", RegexOption.IGNORE_CASE)
    private fun clean(s: String) = s.trim().replace(Regex("\\s+"), " ")
    private fun value(s: String) = s.replace(',', '.').toDoubleOrNull() ?: 0.0
    private fun validName(s: String): Boolean {
        val t = clean(s).trim('|', '-', ':', '.', ' ')
        return t.length >= 4 && t.count { it.isLetter() } >= 3 &&
            !tax.containsMatchIn(t) && !junk.containsMatchIn(t) && !end.containsMatchIn(t)
    }
    private fun date(raw: String): String {
        dateYMD.find(raw)?.let { m ->
            val v = "${m.groupValues[1]}-${m.groupValues[2]}-${m.groupValues[3]}"
            if (runCatching { LocalDate.parse(v) }.isSuccess) return v
        }
        dateDMY.find(raw)?.let { m ->
            val v = "${m.groupValues[3]}-${m.groupValues[2]}-${m.groupValues[1]}"
            if (runCatching { LocalDate.parse(v) }.isSuccess) return v
        }
        return LocalDate.now().toString()
    }
    private fun qty(line: String, name: String): Pair<Double, String> {
        weight.find(line)?.let {
            val q = value(it.groupValues[1])
            if (q in 0.001..20.0) return q to "кг"
        }
        // OCR sometimes loses '0.' in '0.106КГ'. Only restore with price consistency elsewhere.
        pieces.find(line)?.let {
            val q = it.groupValues[1].toDoubleOrNull() ?: 1.0
            if (q in 1.0..99.0) return q to "шт"
        }
        pack.findAll(name).lastOrNull()?.let {
            val q = value(it.groupValues[1]); val u = it.groupValues[2].lowercase(Locale.ROOT)
            if (q > 0) return q to when (u) { "kg", "кг" -> "кг"; "гр", "г" -> "г"; "мл" -> "мл"; else -> "л" }
        }
        return 1.0 to "шт"
    }
    private fun finalPrice(line: String): Double {
        val nums = money.findAll(line).map { value(it.value) }.filter { it > 0 }.toList()
        return nums.lastOrNull() ?: 0.0
    }
    private fun receiptTotal(lines: List<String>): Double {
        val totalLine = Regex("^(?:ИТОГ|ИТОГО|К\\s+ОПЛАТЕ)\\b", RegexOption.IGNORE_CASE)
        for (line in lines.asReversed()) {
            if (!totalLine.containsMatchIn(line)) continue
            val prices = money.findAll(line).map { value(it.value) }.toList()
            if (prices.isNotEmpty()) return prices.last()
        }
        return 0.0
    }
    fun parse(raw: String): Receipt {
        val lines = raw.lines().map(::clean).filter { it.isNotBlank() }
        val store = when {
            raw.contains("ПЯТ", true) || raw.contains("ПЯТЕР", true) || raw.contains("Х5", true) -> "Пятёрочка"
            raw.contains("МАГНИТ", true) -> "Магнит"
            raw.contains("ЛЕНТА", true) -> "Лента"
            else -> "Магазин"
        }
        val result = mutableListOf<Item>()
        var pending: String? = null
        var pendingAt = -1
        for ((index, line) in lines.withIndex()) {
            if (end.containsMatchIn(line)) break
            if (tax.containsMatchIn(line)) {
                val name = pending
                if (name != null && index - pendingAt <= 2) {
                    val price = finalPrice(line)
                    if (price > 0.0) {
                        val (q, u) = qty(line, name)
                        result.add(Item(name, q, u, price))
                    }
                }
                pending = null
                continue
            }
            if (junk.containsMatchIn(line)) { pending = null; continue }
            // Product names often start with a four-digit PLU code.
            val name = clean(line.replace(Regex("^\\d{3,5}\\s+"), "")).trim('|', ' ', '-', '.')
            if (!validName(name)) continue
            val prices = money.findAll(name).toList()
            val qMarker = weight.containsMatchIn(name) || pieces.containsMatchIn(name)
            if (prices.isNotEmpty() && qMarker) {
                val price = finalPrice(name)
                val cleanName = clean(name.substring(0, prices.first().range.first))
                if (validName(cleanName) && price > 0) {
                    val (q, u) = qty(name, cleanName)
                    result.add(Item(cleanName, q, u, price))
                    pending = null
                    continue
                }
            }
            pending = name
            pendingAt = index
        }
        return Receipt(date(raw), store, receiptTotal(lines), result)
    }
}
