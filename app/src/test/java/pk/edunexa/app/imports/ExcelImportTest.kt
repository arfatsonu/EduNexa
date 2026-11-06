package pk.edunexa.app.imports

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

class ExcelImportTest {
    private val config = mapOf<String, Any?>("sessions" to listOf(mapOf("id" to "current", "name" to "2026-2027", "status" to "Active")), "classes" to listOf(mapOf("id" to "c9", "name" to "Class 9", "isActive" to true)), "sections" to listOf(mapOf("id" to "diamond", "classId" to "c9", "name" to "Diamond", "isActive" to true)))
    private val values = listOf("Usman Qamar", "Male", "Class 9", "Diamond", "01", "Qamar", "03001234567", "2800", "4000", "100") + List(8) { "" }
    @Test fun preservesUnicodeLeadingZeroesAndCredentialStringsAcrossExcelRoundtrip() {
        val rows = listOf(listOf("Name", "Phone", "Password"), listOf("عثمان & Qamar", "03001234567", "=literal-password"))
        val bytes = ExcelWorkbook.write(listOf("Students" to rows, "Instructions" to listOf(listOf("Instructions"))))
        assertEquals(rows, ExcelWorkbook.read(bytes, "student"))
    }
    @Test fun validatesLiveClassScopeAndKeepsIdStableAcrossFeeChanges() {
        val plan = ImportPolicy.prepare("school", "student", listOf(ImportPolicy.studentColumns, values), config, emptyList())
        val row = plan.rows.single()
        assertTrue(row.errors.joinToString(), row.errors.isEmpty())
        assertEquals("c9", row.values["classId"]); assertEquals("diamond", row.values["sectionId"])
        assertEquals("2800", row.values["MonthlyFee"]); assertEquals("4000", row.values["AnnualFund"])
        val changed = values.toMutableList().apply { this[7] = "3000" }
        assertEquals(row.id, ImportPolicy.prepare("school", "student", listOf(ImportPolicy.studentColumns, changed), config, emptyList()).rows.single().id)
        val wrong = values.toMutableList().apply { this[3] = "Missing" }
        assertTrue(ImportPolicy.prepare("school", "student", listOf(ImportPolicy.studentColumns, wrong), config, emptyList()).rows.single().errors.any { it.contains("Section") })
    }
    @Test fun detectsFileAndLiveDuplicatesButAllowsSafeResume() {
        val plan = ImportPolicy.prepare("school", "student", listOf(ImportPolicy.studentColumns, values, values), config, emptyList())
        assertTrue(plan.rows[1].errors.any { it.contains("Duplicate") })
        val row = plan.rows.first()
        val profile = mapOf<String, Any?>("id" to row.id, "fullName" to row.name, "classId" to "c9", "sectionId" to "diamond", "rollNo" to "1", "academicSessionId" to "current", "status" to "Active")
        assertTrue(ImportPolicy.prepare("school", "student", listOf(ImportPolicy.studentColumns, values), config, listOf(profile)).rows.single().errors.isEmpty())
        assertTrue(ImportPolicy.prepare("school", "student", listOf(ImportPolicy.studentColumns, values), config, listOf(profile + ("id" to "other"))).rows.single().errors.any { it.contains("already belongs") })
        assertTrue(ImportPolicy.prepare("school", "student", listOf(ImportPolicy.studentColumns, values), config, listOf(profile + ("isDeleted" to true))).rows.single().errors.any { it.contains("removed") })
    }
    @Test fun rejectsInvalidAmountsAndTeacherDuplicatePhones() {
        val bad = values.toMutableList().apply { this[7] = "NaN"; this[8] = "-1" }
        assertTrue(ImportPolicy.prepare("school", "student", listOf(ImportPolicy.studentColumns, bad), config, emptyList()).rows.single().errors.size >= 2)
        val teachers = listOf(ImportPolicy.teacherColumns, listOf("Arfat Mushtaq", "03001234567", "", "", ""), listOf("Second Teacher", "+923001234567", "", "", ""))
        assertTrue(ImportPolicy.prepare("school", "teacher", teachers, config, emptyList()).rows[1].errors.any { it.contains("Duplicate teacher") })
    }
    @Test fun readsSharedStringsAndSparseCellsAndRejectsActualFormulas() {
        val original = ExcelWorkbook.write(listOf("Students" to listOf(listOf("FullName", "Phone"))))
        fun replace(sheet: String): ByteArray {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { output -> ZipInputStream(original.inputStream()).use { input ->
                while(true) { val entry = input.nextEntry ?: break; val bytes = input.readBytes(); output.putNextEntry(ZipEntry(entry.name)); output.write(if(entry.name == "xl/worksheets/sheet1.xml") sheet.toByteArray() else bytes); output.closeEntry() }
                output.putNextEntry(ZipEntry("xl/sharedStrings.xml")); output.write("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><si><t>FullName</t></si><si><r><t>Usman </t></r><r><t>Qamar</t></r></si></sst>".toByteArray()); output.closeEntry()
            } }
            return out.toByteArray()
        }
        val xml = "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData><row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c></row><row r=\"3\"><c r=\"A3\" t=\"s\"><v>1</v></c><c r=\"C3\"><v>2800</v></c></row></sheetData></worksheet>"
        val rows = ExcelWorkbook.read(replace(xml), "student")
        assertEquals(listOf("Usman Qamar", "", "2800"), rows[2])
        try { ExcelWorkbook.read(replace(xml.replace("<v>2800</v>", "<f>1+1</f><v>2</v>")), "student"); fail("Formula accepted") } catch(e: IllegalStateException) { assertTrue(e.message.orEmpty().contains("formula")) }
    }
}
