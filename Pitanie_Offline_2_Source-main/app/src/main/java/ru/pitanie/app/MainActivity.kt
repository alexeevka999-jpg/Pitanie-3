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

    private fun home() {

        start("Покупки по месяцам")

        val monthText = month.format(
            DateTimeFormatter.ofPattern(
                "LLLL yyyy",
                Locale("ru", "RU")
            )
        )

        label(monthText, 23, true, green)

        button("◀ Предыдущий месяц") {
            month = month.minusMonths(1)
            home()
        }

        button("Следующий месяц ▶") {
            month = month.plusMonths(1)
            home()
        }

        val selected = receipts.filter {
            it.date.startsWith(month.toString())
        }

        val monthlyTotal = selected.sumOf {
            receiptTotal(it)
        }

        val allTotal = receipts.sumOf {
            receiptTotal(it)
        }

        val monthlyItems = selected.flatMap {
            it.items
        }

        label(
            "ПОТРАЧЕНО ЗА МЕСЯЦ",
            18,
            true,
            green
        )

        label(
            money(monthlyTotal),
            33,
            true
        )

        label(
            "⚖ Общий вес: ${weightLabel(monthlyItems)}",
            25,
            true,
            green
        )

        label(
            "${selected.size} покупок · " +
            "${monthlyItems.size} товаров"
        )

        label(
            "За всё время: ${money(allTotal)} · " +
            weightLabel(receipts.flatMap { it.items })
        )

        button("＋ Добавить покупку вручную") {

            edit(
                Receipt(
                    LocalDate.now().toString(),
                    "Магазин",
                    0.0,
                    emptyList()
                ),
                false
            )
        }

        button("📷 Сфотографировать чек") {
            takePhoto()
        }

        button("▧ Выбрать фото чека") {

            val intent = Intent(
                Intent.ACTION_GET_CONTENT
            ).apply {
                type = "image/*"
            }

            startActivityForResult(intent, pick)
        }

        categories(monthlyItems)

        label(
            "ПОКУПКИ ЗА МЕСЯЦ",
            19,
            true,
            green
        )

        selected.reversed().forEach { receipt ->

            button(
                "${receipt.date}  ${receipt.store}\n" +
                "${money(receiptTotal(receipt))} · " +
                "${receipt.items.size} товаров · " +
                weightLabel(receipt.items)
            ) {

                edit(receipt, true)
            }
        }

        button("📈 Изменение цен") {
            prices()
        }

        button("🍏 Калорийность продуктов") {
            food()
        }

        button("Резервная копия") {
            backup()
        }

        button("Восстановить из файла") {
            restore()
        }

        label(
            "Все данные хранятся на телефоне. " +
            "Для OCR используется локальное распознавание.",
            13
        )
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

                    api.setImage(bitmap)

                    api.getUTF8Text() ?: ""

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
