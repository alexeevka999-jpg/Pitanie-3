package ru.pitanie.app

import android.app.*
import android.os.Bundle
import android.content.*
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import android.view.*
import android.widget.*
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import androidx.core.content.FileProvider
import com.googlecode.tesseract.android.TessBaseAPI
import org.json.JSONArray
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : Activity() {

    private val green = Color.rgb(15, 91, 50)

    private lateinit var root: LinearLayout

    private val receipts = mutableListOf<Receipt>()

    private var month = YearMonth.now()

    private var photoUri: Uri? = null

    private val pick = 100
    private val camera = 101
    private val restoreCode = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        load()
        home()
    }

    private fun save() {
        val array = JSONArray()

        receipts.forEach {
            array.put(it.json())
        }

        getSharedPreferences("db", MODE_PRIVATE)
            .edit()
            .putString("receipts", array.toString())
            .apply()
    }

    private fun load() {
        runCatching {
            val array = JSONArray(
                getSharedPreferences("db", MODE_PRIVATE)
                    .getString("receipts", "[]")
            )

            receipts.clear()

            for (i in 0 until array.length()) {
                receipts.add(
                    Receipt.from(array.getJSONObject(i))
                )
            }
        }
    }

    private fun start(title: String) {

        window.statusBarColor = Color.rgb(247, 249, 246)
        window.navigationBarColor = Color.BLACK

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 16)
            setBackgroundColor(Color.rgb(247, 249, 246))
        }

        val scroll = ScrollView(this)
        scroll.addView(root)

        setContentView(scroll)

        label("ПИТАНИЕ", 23, true, green)
        label(title, 29, true)
    }

    private fun label(
        textValue: String,
        size: Int = 17,
        bold: Boolean = false,
        color: Int = Color.rgb(30, 45, 36)
    ) {

        root.addView(
            TextView(this).apply {
                text = textValue
                textSize = size.toFloat()
                setTextColor(color)
                setPadding(4, 9, 4, 9)

                if (bold) {
                    setTypeface(null, Typeface.BOLD)
                }
            }
        )
    }

    private fun button(
        textValue: String,
        action: () -> Unit
    ) {

        root.addView(
            Button(this).apply {
                text = textValue
                textSize = 17f
                isAllCaps = false

                setOnClickListener {
                    action()
                }
            }
        )
    }

    private fun money(value: Double): String {
        return String.format(
            Locale("ru", "RU"),
            "%,.2f ₽",
            value
        )
    }

    private fun number(value: String): Double {
        return value
            .replace(',', '.')
            .replace(" ", "")
            .toDoubleOrNull() ?: 0.0
    }

    private fun weightGrams(items: List<Item>): Double {

        return items.sumOf { item ->

            when (
                item.unit.trim().lowercase(Locale.ROOT)
            ) {

                "кг", "kg" -> item.qty * 1000.0

                "г", "гр", "g" -> item.qty

                else -> 0.0
            }
        }
    }

    private fun weightLabel(items: List<Item>): String {

        val grams = weightGrams(items)

        return String.format(
            Locale("ru", "RU"),
            "%.3f кг",
            grams / 1000.0
        )
    }

    private fun category(item: Item): String {

        val name = item.name.lowercase(Locale.ROOT)

        return when {

            listOf("яйц", "яйцо").any {
                name.contains(it)
            } -> "Яйца"

            listOf(
                "масло слив",
                "масло раст",
                "подсолнечное масло",
                "оливковое масло"
            ).any {
                name.contains(it)
            } -> "Масло"

            listOf(
                "молоко",
                "кефир",
                "йогурт",
                "сметан",
                "творог",
                "сыр"
            ).any {
                name.contains(it)
            } -> "Молочные продукты"

            listOf(
                "говядин",
                "свинин",
                "куриц",
                "индейк",
                "фарш",
                "мясо"
            ).any {
                name.contains(it)
            } -> "Мясо"

            listOf(
                "рыба",
                "сёмг",
                "форел",
                "сельд",
                "стерляд"
            ).any {
                name.contains(it)
            } -> "Рыба"

            listOf(
                "яблок",
                "банан",
                "лимон",
                "груш",
                "апельсин",
                "манго"
            ).any {
                name.contains(it)
            } -> "Фрукты"

            listOf(
                "картоф",
                "морков",
                "огур",
                "помидор",
                "капуст",
                "лук",
                "имбир"
            ).any {
                name.contains(it)
            } -> "Овощи"

            listOf(
                "хлеб",
                "батон",
                "булк",
                "лаваш"
            ).any {
                name.contains(it)
            } -> "Хлеб"

            listOf(
                "квас",
                "сок",
                "вода",
                "напиток",
                "лимонад"
            ).any {
                name.contains(it)
            } -> "Напитки"

            else -> "Прочие продукты"
        }
    }

    private fun unitTotals(items: List<Item>): String {

        var grams = 0.0
        var liters = 0.0
        var pieces = 0.0

        for (item in items) {

            when (
                item.unit.trim().lowercase(Locale.ROOT)
            ) {

                "кг", "kg" -> grams += item.qty * 1000

                "г", "гр", "g" -> grams += item.qty

                "л", "l" -> liters += item.qty

                "мл", "ml" -> liters += item.qty / 1000

                "шт", "штук", "уп" -> pieces += item.qty
            }
        }

        val result = mutableListOf<String>()

        if (grams > 0) {

            result += if (grams >= 1000) {

                String.format(
                    Locale("ru", "RU"),
                    "%.3f кг",
                    grams / 1000
                )

            } else {

                String.format(
                    Locale("ru", "RU"),
                    "%.0f г",
                    grams
                )
            }
        }

        if (liters > 0) {

            result += String.format(
                Locale("ru", "RU"),
                "%.3f л",
                liters
            )
        }

        if (pieces > 0) {

            result += String.format(
                Locale("ru", "RU"),
                "%.0f шт.",
                pieces
            )
        }

        return result
            .ifEmpty {
                listOf("Количество не указано")
            }
            .joinToString(" · ")
    }

    private fun categories(items: List<Item>) {

        label(
            "ПО КАТЕГОРИЯМ",
            21,
            true,
            green
        )

        items.groupBy(::category)
            .toSortedMap()
            .forEach { (categoryName, values) ->

                button(
                    "$categoryName\n" +
                    "${unitTotals(values)} · " +
                    money(values.sumOf { it.total })
                ) {

                    start(categoryName)

                    values.forEach {

                        label(
                            "${it.name} — " +
                            "${unitTotals(listOf(it))} — " +
                            money(it.total)
                        )
                    }

                    button("← К покупкам") {
                        home()
                    }
                }
            }
    }

    private fun receiptTotal(receipt: Receipt): Double {

        return if (receipt.printedTotal > 0) {
            receipt.printedTotal
        } else {
            receipt.items.sumOf { it.total }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun card(textValue: String, action: (() -> Unit)? = null, prominent: Boolean = false) {
        val view = TextView(this).apply {
            text = textValue
            textSize = if (prominent) 19f else 16f
            setTextColor(if (prominent) Color.WHITE else Color.rgb(27, 49, 36))
            setTypeface(null, if (prominent) Typeface.BOLD else Typeface.NORMAL)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(17), dp(13), dp(15), dp(13))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(13).toFloat()
                setColor(if (prominent) green else Color.WHITE)
                setStroke(dp(1), Color.rgb(218, 228, 221))
            }
            if (action != null) setOnClickListener { action() }
        }
        root.addView(view, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(9)
        })
    }

    private fun section(textValue: String) {
        label(textValue, 16, true, green)
    }

    private fun navBar() {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(9), 0, dp(5))
        }
        val entries = listOf(
            "🛒\nПокупки" to { home() },
            "🍏\nПитание" to { food() },
            "📈\nЦены" to { prices() },
            "▤\nОтчёты" to { reports() },
            "☰\nЕщё" to { moreMenu() }
        )
        entries.forEach { (title, action) ->
            val view = TextView(this).apply {
                text = title
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(green)
                setPadding(dp(2), dp(12), dp(2), dp(12))
                setTypeface(null, Typeface.BOLD)
                setOnClickListener { action() }
            }
            bar.addView(view, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(bar)
    }

    private fun reports() {
        start("Отчёты")
        val byYear = receipts.groupBy { it.date.take(4) }.toSortedMap(compareByDescending { it })
        if (byYear.isEmpty()) label("Пока нет покупок")
        byYear.forEach { (year, values) ->
            card("📅 $year    ${money(values.sumOf { receiptTotal(it) })}\n${values.size} покупок · ${values.sumOf { it.items.size }} товаров")
        }
        button("← Покупки") { home() }
    }

    private fun moreMenu() {
        start("Ещё")
        card("💾 Создать резервную копию") { backup() }
        card("↩ Восстановить из файла") { restore() }
        card("📊 Отчёты по годам") { reports() }
        label("Все покупки хранятся на телефоне. Распознавание работает без интернета.", 14)
        button("← Покупки") { home() }
    }

    private fun home() {
        start("Покупки по месяцам")
        val monthText = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru", "RU")))
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun monthControl(textValue: String, weight: Float, action: () -> Unit) {
            controls.addView(TextView(this).apply {
                text = textValue
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(green)
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(3), dp(12), dp(3), dp(12))
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(0, -2, weight))
        }
        monthControl("❮", 1f) { month = month.minusMonths(1); home() }
        monthControl("🗓 $monthText", 5f) { month = YearMonth.now(); home() }
        monthControl("❯", 1f) { month = month.plusMonths(1); home() }
        root.addView(controls)

        val selected = receipts.filter { it.date.startsWith(month.toString()) }
        val monthlyItems = selected.flatMap { it.items }
        val monthlyTotal = selected.sumOf { receiptTotal(it) }
        val totalItems = receipts.sumOf { it.items.size }
        val allTotal = receipts.sumOf { receiptTotal(it) }

        val summary = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(13), dp(18), dp(13))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(15).toFloat()
                setColor(Color.rgb(222, 243, 229))
            }
        }
        fun summaryLine(value: String, size: Float, bold: Boolean = false) {
            summary.addView(TextView(this).apply {
                text = value
                textSize = size
                setTextColor(Color.rgb(25, 62, 42))
                if (bold) setTypeface(null, Typeface.BOLD)
            })
        }
        summaryLine("🛒  ПОТРАЧЕНО ЗА МЕСЯЦ", 15f, true)
        summaryLine(money(monthlyTotal), 31f, true)
        summaryLine("${selected.size} покупок · ${monthlyItems.size} товаров · ${weightLabel(monthlyItems)}", 14f)
        root.addView(summary, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        section("КОЛИЧЕСТВО ПО КАТЕГОРИЯМ")
        val grouped = monthlyItems.groupBy(::category)
        val categoryIcons = listOf("Мясо" to "🥩", "Рыба" to "🐟", "Молочные продукты" to "🧀", "Овощи" to "🥬", "Фрукты" to "🍎")
        val categoriesRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        categoryIcons.forEach { (name, icon) ->
            val values = grouped[name].orEmpty()
            categoriesRow.addView(TextView(this).apply {
                text = "$icon\n${if (name == "Молочные продукты") "Молочные" else name}\n${money(values.sumOf { it.total })}"
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(24, 55, 38))
                setPadding(dp(2), dp(9), dp(2), dp(9))
                setOnClickListener {
                    start(name)
                    values.forEach { label("${it.name} — ${money(it.total)}") }
                    button("← Покупки") { home() }
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(categoriesRow)
        card("📦  Остальные категории  ›") { categories(monthlyItems) }
        card("▱  За всё время: ${money(allTotal)} · ${receipts.size} покупок · $totalItems товаров") { reports() }

        card("⊕  Добавить покупку вручную") {
            edit(Receipt(LocalDate.now().toString(), "Магазин", 0.0, emptyList()), false)
        }
        card("📷  Сфотографировать чек", { takePhoto() }, true)
        card("▧  Выбрать фото чека") {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            startActivityForResult(intent, pick)
        }

        section("ПОКУПКИ ЗА МЕСЯЦ")
        if (selected.isEmpty()) label("За этот месяц покупок пока нет", 15)
        selected.sortedByDescending { it.date }.forEach { receipt ->
            card("🛍  ${receipt.store}                         ${money(receiptTotal(receipt))}\n" +
                 "${receipt.date} · ${receipt.items.size} товаров · ${weightLabel(receipt.items)}  ›") {
                edit(receipt, true)
            }
        }
        section("ИТОГИ ПО ГОДАМ")
        card("📅  ${month.year}    ${money(receipts.filter { it.date.startsWith(month.year.toString()) }.sumOf { receiptTotal(it) })}  ›") { reports() }
        navBar()
        label("🔒 Данные и распознавание чеков остаются на телефоне.", 12)
    }

    private fun input(
        hintText: String,
        value: String
    ): EditText {

        val editText = EditText(this).apply {

            setText(value)
            textSize = 18f
            hint = hintText
            setSingleLine(true)
        }

        root.addView(editText)

        return editText
    }

    private fun edit(
        receipt: Receipt,
        existing: Boolean
    ) {

        editWithItems(
            receipt,
            existing,
            receipt.items.toMutableList()
        )
    }

    private fun editWithItems(
        original: Receipt,
        existing: Boolean,
        items: MutableList<Item>
    ) {

        start("Проверка чека")

        label("Дата (ГГГГ-ММ-ДД)")

        val date = input(
            "Дата",
            original.date
        )

        val store = input(
            "Магазин",
            original.store
        )

        val printed = input(
            "Итог по чеку, ₽",
            if (original.printedTotal == 0.0) {
                ""
            } else {
                original.printedTotal.toString()
            }
        )

        fun currentReceipt(): Receipt {

            return Receipt(
                date.text.toString(),
                store.text.toString(),
                number(printed.text.toString()),
                items.toList()
            )
        }

        label(
            "Товары: ${items.size}",
            20,
            true,
            green
        )

        items.forEachIndexed { index, item ->

            button(
                "${item.name} · " +
                "${item.qty} ${item.unit} · " +
                money(item.total)
            ) {

                editItem(item) { updated ->

                    val updatedItems = items.toMutableList()

                    updatedItems[index] = updated

                    editWithItems(
                        currentReceipt(),
                        existing,
                        updatedItems
                    )
                }
            }
        }

        button("＋ Добавить товар") {

            editItem(
                Item(
                    "",
                    1.0,
                    "шт",
                    0.0
                )
            ) { newItem ->

                val updatedItems = items.toMutableList()

                updatedItems.add(newItem)

                editWithItems(
                    currentReceipt(),
                    existing,
                    updatedItems
                )
            }
        }

        val sum = items.sumOf {
            it.total
        }

        label(
            "Сумма товаров: ${money(sum)} · " +
            "⚖ ${weightLabel(items)}",
            20,
            true
        )

        val printedTotal = number(
            printed.text.toString()
        )

        if (
            printedTotal > 0 &&
            kotlin.math.abs(sum - printedTotal) > 0.01
        ) {

            label(
                "⚠ Сумма позиций отличается от " +
                "итога чека. Проверьте товары и скидки.",
                16,
                true,
                Color.RED
            )
        }

        button("✓ Сохранить чек") {

            if (items.isEmpty()) {

                Toast.makeText(
                    this,
                    "Нельзя сохранить пустой чек",
                    Toast.LENGTH_LONG
                ).show()

                return@button
            }

            val finalReceipt = currentReceipt()

            if (existing) {
                receipts.remove(original)
            }

            receipts.add(
                finalReceipt.copy(
                    printedTotal =
                        finalReceipt.printedTotal
                            .takeIf { it > 0 }
                            ?: items.sumOf { it.total }
                )
            )

            save()
            home()
        }
    }

    private fun editItem(
        item: Item,
        done: (Item) -> Unit
    ) {

        val box = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL
            setPadding(22, 12, 22, 0)
        }

        fun field(
            hintText: String,
            value: String
        ): EditText {

            return EditText(this).apply {

                hint = hintText
                setText(value)

                box.addView(this)
            }
        }

        val name = field(
            "Название",
            item.name
        )

        val qty = field(
            "Вес/количество",
            item.qty.toString()
        )

        val unit = field(
            "Единица: кг, г, л или шт",
            item.unit
        )

        val price = field(
            "Сумма, ₽",
            item.total.toString()
        )

        val kcal = field(
            "Ккал на 100 г (необязательно)",
            item.kcal100.toString()
        )

        AlertDialog.Builder(this)
            .setTitle("Товар")
            .setView(box)
            .setPositiveButton("Сохранить") { _, _ ->

                done(
                    Item(
                        name.text.toString(),
                        number(qty.text.toString()),
                        unit.text.toString(),
                        number(price.text.toString()),
                        number(kcal.text.toString())
                    )
                )
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun takePhoto() {

        try {

            val file = File(
                cacheDir,
                "receipt_${System.currentTimeMillis()}.jpg"
            )

            photoUri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )

            val intent = Intent(
                MediaStore.ACTION_IMAGE_CAPTURE
            ).apply {

                putExtra(
                    MediaStore.EXTRA_OUTPUT,
                    photoUri
                )

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }

            startActivityForResult(intent, camera)

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "Камера недоступна: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @Deprecated("Legacy activity result")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (resultCode != RESULT_OK) {
            return
        }

        when (requestCode) {

            pick -> {

                data?.data?.let {
                    recognize(it)
                }
            }

            camera -> {

                val uri = photoUri

                if (uri != null) {

                    recognize(uri)

                } else {

                    Toast.makeText(
                        this,
                        "Не удалось получить фотографию",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            restoreCode -> {

                data?.data?.let { uri ->

                    runCatching {

                        val text = contentResolver
                            .openInputStream(uri)!!
                            .bufferedReader()
                            .use {
                                it.readText()
                            }

                        val array = JSONArray(text)

                        val restored = mutableListOf<Receipt>()

                        for (i in 0 until array.length()) {

                            restored.add(
                                Receipt.from(
                                    array.getJSONObject(i)
                                )
                            )
                        }

                        receipts.clear()
                        receipts.addAll(restored)

                        save()
                        home()

                    }.onFailure {

                        Toast.makeText(
                            this,
                            "Ошибка восстановления: ${it.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    // Local grayscale + adaptive threshold. No uploads or external services.
    private fun enhanceReceipt(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        if (width < 10 || height < 10) return source
        val scale = (1800.0 / width).coerceIn(1.0, 2.0)
        val working = if (scale > 1.05) Bitmap.createScaledBitmap(
            source, (width * scale).toInt(), (height * scale).toInt(), true
        ) else source
        val w = working.width
        val h = working.height
        val pixels = IntArray(w * h)
        working.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = IntArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            gray[i] = ((android.graphics.Color.red(c) * 299 +
                android.graphics.Color.green(c) * 587 +
                android.graphics.Color.blue(c) * 114) / 1000)
        }
        // Integral image makes local background estimation linear in image size.
        val integral = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var row = 0L
            for (x in 0 until w) {
                row += gray[y * w + x]
                integral[(y + 1) * (w + 1) + x + 1] =
                    integral[y * (w + 1) + x + 1] + row
            }
        }
        val radius = 25
        for (y in 0 until h) {
            val top = (y - radius).coerceAtLeast(0)
            val bottom = (y + radius + 1).coerceAtMost(h)
            for (x in 0 until w) {
                val left = (x - radius).coerceAtLeast(0)
                val right = (x + radius + 1).coerceAtMost(w)
                val sum = integral[bottom * (w + 1) + right] -
                    integral[top * (w + 1) + right] -
                    integral[bottom * (w + 1) + left] +
                    integral[top * (w + 1) + left]
                val avg = sum / ((bottom - top) * (right - left))
                val ink = gray[y * w + x] < avg - 13
                pixels[y * w + x] = if (ink) android.graphics.Color.BLACK
                    else android.graphics.Color.WHITE
            }
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        if (working !== source) working.recycle()
        return result
    }

    private fun recognize(uri: Uri) {

        start("Распознавание чека")

        label(
            "Распознаём чек без интернета…",
            18,
            true,
            green
        )

        Thread {

            try {

                val dir = File(
                    filesDir,
                    "ocr"
                )

                val tess = File(
                    dir,
                    "tessdata"
                )

                tess.mkdirs()

                for (lang in listOf("rus", "eng")) {

                    val destination = File(
                        tess,
                        "$lang.traineddata"
                    )

                    if (!destination.exists()) {

                        assets.open(
                            "tessdata/$lang.traineddata"
                        ).use { input ->

                            destination.outputStream().use {
                                output ->

                                input.copyTo(output)
                            }
                        }
                    }
                }

                val bitmap = if (
                    Build.VERSION.SDK_INT >= 28
                ) {

                    ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(
                            contentResolver,
                            uri
                        )
                    ) { decoder, _, _ ->

                        decoder.allocator =
                            ImageDecoder.ALLOCATOR_SOFTWARE
                    }

                } else {

                    contentResolver.openInputStream(uri).use {

                        BitmapFactory.decodeStream(it)
                    }
                }

                if (bitmap == null) {
                    throw IllegalStateException(
                        "Не удалось открыть изображение"
                    )
                }

                val api = TessBaseAPI()

                if (!api.init(dir.absolutePath, "rus+eng")) {

                    api.recycle()

                    bitmap.recycle()

                    throw IllegalStateException(
                        "Не удалось загрузить русский OCR"
                    )
                }

                val raw = try {

                    // Improve faint thermal printing before local OCR.
                    val prepared = enhanceReceipt(bitmap)
                    try {
                        api.setImage(prepared)
                        api.getUTF8Text() ?: ""
                    } finally {
                        if (prepared !== bitmap) prepared.recycle()
                    }

                } finally {

                    api.recycle()

                    bitmap.recycle()
                }

                val parsed = ReceiptParser.parse(raw)

                runOnUiThread {

                    if (parsed.items.isEmpty()) {

                        Toast.makeText(
                            this,
                            "Товары не найдены. Проверьте текст OCR.",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    edit(parsed, false)

                    button(
                        "📄 Показать распознанный текст"
                    ) {

                        start("Текст чека (OCR)")

                        label(raw, 14)

                        button(
                            "← Вернуться к проверке"
                        ) {

                            edit(parsed, false)
                        }
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    Toast.makeText(
                        this,
                        "Ошибка OCR: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()

                    home()
                }
            }

        }.start()
    }

    private fun prices() {

        start("Изменение цен")

        val history = receipts.flatMap { receipt ->

            receipt.items.map {
                receipt.date to it
            }

        }.groupBy {

            it.second.name.lowercase().trim()
        }

        history.forEach { (name, values) ->

            if (values.size >= 2) {

                val sorted = values.sortedBy {
                    it.first
                }

                val first = sorted[
                    sorted.size - 2
                ].second

                val last = sorted.last().second

                val oldPrice =
                    first.total /
                    first.qty.coerceAtLeast(0.0001)

                val newPrice =
                    last.total /
                    last.qty.coerceAtLeast(0.0001)

                val change = if (oldPrice > 0) {

                    (newPrice / oldPrice - 1) * 100

                } else {
                    0.0
                }

                val symbol = if (change > 0) {
                    "🔴"
                } else {
                    "🟢"
                }

                label(
                    "$name:\n" +
                    "${money(oldPrice)} → " +
                    "${money(newPrice)} / ${last.unit}\n" +
                    "$symbol " +
                    String.format(
                        Locale.US,
                        "%+.1f%%",
                        change
                    )
                )
            }
        }

        button("← Назад") {
            home()
        }
    }

    private fun food() {

        start("Калорийность продуктов")

        label(
            "Оценка калорийности купленных " +
            "продуктов, а не съеденных."
        )

        receipts.flatMap {
            it.items
        }.forEach { item ->

            if (item.kcal100 > 0) {

                val grams = when (
                    item.unit.trim().lowercase()
                ) {

                    "кг" -> item.qty * 1000

                    "г", "гр" -> item.qty

                    else -> 0.0
                }

                val calories =
                    grams * item.kcal100 / 100

                label(
                    "${item.name}: " +
                    String.format(
                        Locale("ru", "RU"),
                        "%.0f",
                        calories
                    ) +
                    " ккал"
                )
            }
        }

        label(
            "Для расчёта укажите калорийность " +
            "на 100 г в карточке товара."
        )

        button("← Назад") {
            home()
        }
    }

    private fun backup() {

        try {

            val file = File(
                cacheDir,
                "pitanie_backup.json"
            )

            val array = JSONArray()

            receipts.forEach {
                array.put(it.json())
            }

            file.writeText(
                array.toString(2)
            )

            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )

            val intent = Intent(
                Intent.ACTION_SEND
            ).apply {

                type = "application/json"

                putExtra(
                    Intent.EXTRA_STREAM,
                    uri
                )

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            startActivity(
                Intent.createChooser(
                    intent,
                    "Сохранить резервную копию"
                )
            )

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "Ошибка создания копии: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun restore() {

        val intent = Intent(
            Intent.ACTION_GET_CONTENT
        ).apply {

            type = "application/json"
            addCategory(
                Intent.CATEGORY_OPENABLE
            )
        }

        startActivityForResult(
            intent,
            restoreCode
        )
    }
}
