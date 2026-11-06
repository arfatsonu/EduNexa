package pk.edunexa.app.imports

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Bounded OOXML reader; never evaluates formulas or opens external relationships. */
object ExcelWorkbook {
    const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    fun readLimited(input: InputStream, limit: Int = 5_000_000): ByteArray {
        val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size() + n <= limit) { "Excel file is too large. Use a smaller file without embedded images." }; out.write(buffer, 0, n) }
        return out.toByteArray()
    }
    private fun parser(bytes: ByteArray): XmlPullParser {
        val text = bytes.toString(Charsets.UTF_8).replace("\u0000", "")
        require(!Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(text)) { "Unsupported XML in Excel file." }
        return XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser().apply { setInput(ByteArrayInputStream(bytes), null) }
    }
    fun read(bytes: ByteArray, role: String): List<List<String>> {
        require(bytes.size <= 5_000_000 && bytes.take(2) == listOf(80.toByte(), 75.toByte())) { "Select an Excel .xlsx file. Save old .xls files as .xlsx first." }
        val parts = mutableMapOf<String, ByteArray>(); var total = 0; var count = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++count <= 200) { "Excel file contains too many parts." }
                require(!entry.name.contains("..") && !entry.name.startsWith("/") && !entry.name.contains("vbaProject", true)) { "Unsupported Excel package." }
                val content = readLimited(zip, 4_000_000); total += content.size
                require(total <= 12_000_000) { "Excel workbook is too large after unpacking." }
                require(entry.name !in parts) { "Duplicate Excel package parts." }
                parts[entry.name] = content; zip.closeEntry()
            }
        }
        val workbook = parser(parts["xl/workbook.xml"] ?: error("Invalid Excel workbook."))
        val sheets = mutableListOf<Pair<String, String>>()
        while (workbook.next() != XmlPullParser.END_DOCUMENT) if (workbook.eventType == XmlPullParser.START_TAG && workbook.name == "sheet") {
            val id = (0 until workbook.attributeCount).firstOrNull { workbook.getAttributeName(it) == "id" }?.let { workbook.getAttributeValue(it) }.orEmpty()
            sheets += workbook.getAttributeValue(null, "name").orEmpty() to id
        }
        val targetSheet = sheets.firstOrNull { it.first.equals(if (role == "student") "Students" else "Teachers", true) } ?: sheets.firstOrNull() ?: error("The Excel workbook has no worksheet.")
        val rels = parser(parts["xl/_rels/workbook.xml.rels"] ?: error("Missing Excel worksheet links.")); var target = ""
        while (rels.next() != XmlPullParser.END_DOCUMENT) if (rels.eventType == XmlPullParser.START_TAG && rels.name == "Relationship" && rels.getAttributeValue(null, "Id") == targetSheet.second) {
            require(rels.getAttributeValue(null, "TargetMode") != "External") { "External worksheets are unsupported." }
            target = rels.getAttributeValue(null, "Target").orEmpty()
        }
        require(target.isNotBlank() && !target.contains("..")) { "Invalid Excel worksheet link." }
        val path = if (target.startsWith("/")) target.drop(1) else "xl/$target"
        val shared = mutableListOf<String>()
        parts["xl/sharedStrings.xml"]?.let { bytesPart ->
            val p = parser(bytesPart); var text = StringBuilder()
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG && p.name == "si") text = StringBuilder()
                else if (p.eventType == XmlPullParser.START_TAG && p.name == "t") text.append(p.nextText())
                else if (p.eventType == XmlPullParser.END_TAG && p.name == "si") { require(shared.size < 20000); shared += text.toString() }
            }
        }
        val rows = mutableListOf<List<String>>(); val p = parser(parts[path] ?: error("Excel worksheet was not found."))
        var row = mutableMapOf<Int, String>(); var rowNumber = 0; var column = 0; var type = ""; var value = StringBuilder()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG) when (p.name) {
                "row" -> { row = mutableMapOf(); rowNumber = p.getAttributeValue(null, "r")?.toIntOrNull() ?: rows.size + 1; require(rowNumber in 1..1000) { "Use a clean template with at most 100 data rows." } }
                "c" -> { val ref = p.getAttributeValue(null, "r").orEmpty().takeWhile { it.isLetter() }; column = ref.fold(0) { result, char -> result * 26 + (char.uppercaseChar() - 'A' + 1) } - 1; require(column in 0..63) { "Too many Excel columns." }; type = p.getAttributeValue(null, "t").orEmpty(); value = StringBuilder() }
                "v", "t" -> value.append(p.nextText())
                "f" -> error("Row $rowNumber contains a formula. Paste its calculated value as plain data first.")
            } else if (p.eventType == XmlPullParser.END_TAG) when (p.name) {
                "c" -> {
                    require(type != "e") { "Row $rowNumber contains an Excel error." }
                    val raw = value.toString()
                    row[column] = if (type == "s") shared.getOrNull(raw.toIntOrNull() ?: -1) ?: error("Invalid shared string in row $rowNumber.") else raw
                }
                "row" -> { while (rows.size < rowNumber - 1) rows += emptyList<String>(); rows += (0..(row.keys.maxOrNull() ?: -1)).map { row[it].orEmpty() } }
            }
        }
        return rows
    }
    private fun escape(value: String) = value.filter { it >= ' ' || it in "\n\r\t" }.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun column(index: Int): String { var n = index + 1; var result = ""; while (n > 0) { n--; result = ('A' + n % 26) + result; n /= 26 }; return result }
    fun write(sheets: List<Pair<String, List<List<String>>>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun part(name: String, xml: String) { zip.putNextEntry(ZipEntry(name)); zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + xml).toByteArray()); zip.closeEntry() }
            val main = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
            val rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
            part("[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" + sheets.indices.joinToString("") { "<Override PartName=\"/xl/worksheets/sheet${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" } + "</Types>")
            part("_rels/.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"$rel/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
            part("xl/workbook.xml", "<workbook xmlns=\"$main\" xmlns:r=\"$rel\"><sheets>" + sheets.mapIndexed { i, sheet -> "<sheet name=\"${escape(sheet.first)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>" }.joinToString("") + "</sheets></workbook>")
            part("xl/_rels/workbook.xml.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" + sheets.indices.joinToString("") { "<Relationship Id=\"rId${it + 1}\" Type=\"$rel/worksheet\" Target=\"worksheets/sheet${it + 1}.xml\"/>" } + "<Relationship Id=\"styles\" Type=\"$rel/styles\" Target=\"styles.xml\"/></Relationships>")
            part("xl/styles.xml", "<styleSheet xmlns=\"$main\"><fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font></fonts><fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF0B1426\"/><bgColor indexed=\"64\"/></patternFill></fill></fills><borders count=\"1\"><border/></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"3\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"49\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/><xf numFmtId=\"49\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>")
            sheets.forEachIndexed { i, sheet ->
                val width = sheet.second.maxOfOrNull { it.size } ?: 1
                val rows = sheet.second.mapIndexed { rowIndex, values -> "<row r=\"${rowIndex + 1}\">" + values.mapIndexed { col, value -> "<c r=\"${column(col)}${rowIndex + 1}\" s=\"${if (rowIndex == 0) 2 else 1}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${escape(value)}</t></is></c>" }.joinToString("") + "</row>" }.joinToString("")
                part("xl/worksheets/sheet${i + 1}.xml", "<worksheet xmlns=\"$main\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><cols><col min=\"1\" max=\"$width\" width=\"24\" style=\"1\" customWidth=\"1\"/></cols><sheetData>$rows</sheetData></worksheet>")
            }
        }
        return out.toByteArray()
    }
}
