import os, re

files_to_check = [
    "app/src/main/java/pk/edunexa/app/ui/TeacherPortalScreen.kt",
    "app/src/main/java/pk/edunexa/app/ui/NotificationsScreen.kt",
    "app/src/main/java/pk/edunexa/app/ui/AdminFeesScreen.kt",
    "app/src/main/java/pk/edunexa/app/ui/ExamScreen.kt",
    "app/src/main/java/pk/edunexa/app/exams/ExamRepository.kt",
    "app/src/main/java/pk/edunexa/app/ui/StudentPortalScreen.kt",
    "app/src/main/java/pk/edunexa/app/students/StudentPortalRepository.kt",
    "app/src/main/java/pk/edunexa/app/ui/TimetableScreen.kt",
    "app/src/main/java/pk/edunexa/app/timetable/TimetableRepository.kt",
    "app/src/main/java/pk/edunexa/app/notifications/DirectMessageHelper.kt",
    "app/src/main/java/pk/edunexa/app/ui/SchoolBranding.kt",
    "app/src/main/java/pk/edunexa/app/ui/PortalNavigationDrawer.kt",
    "app/src/main/java/pk/edunexa/app/ui/AdminAttendanceScreen.kt",
    "app/src/main/java/pk/edunexa/app/ui/AdminDashboardScreen.kt",
    "app/src/main/java/pk/edunexa/app/ui/AdminDiaryScreen.kt",
    "app/src/main/java/pk/edunexa/app/ui/TeacherDiaryScreen.kt",
    "app/src/main/java/pk/edunexa/app/ui/AdminTeachersScreen.kt"
]

all_clean = True
for fpath in files_to_check:
    if not os.path.exists(fpath):
        print(f"MISSING FILE: {fpath}")
        all_clean = False
        continue
    with open(fpath, "r", encoding="utf-8") as fp:
        content = fp.read()

    # Check for invalid escape sequences
    # Valid escapes in Kotlin single-line string: \t, \b, \n, \r, \', \", \\, \$
    lines = content.splitlines()
    for lineno, line in enumerate(lines, 1):
        if line.strip().startswith("//"):
            continue
        # Find single quoted strings (double quote strings)
        dq_strings = re.findall(r'"([^"\\]*(?:\\.[^"\\]*)*)"', line)
        for s in dq_strings:
            bad_escapes = re.findall(r'\\([^tbnrf"\'$\\])', s)
            if bad_escapes:
                print(f"{fpath}:{lineno} -> Invalid escape \\{bad_escapes[0]} in string: {s}")
                all_clean = False

    # Check required imports
    if ".sp" in content:
        has_sp = re.search(r"\b\d+(\.\d+)?\.sp\b", content)
        if has_sp and "import androidx.compose.ui.unit.sp" not in content and "import androidx.compose.ui.unit.*" not in content:
            print(f"{fpath} -> Missing .sp import")
            all_clean = False

    if "Alignment." in content:
        if "import androidx.compose.ui.Alignment" not in content and "import androidx.compose.ui.*" not in content:
            print(f"{fpath} -> Missing Alignment import")
            all_clean = False

    if "FontWeight." in content:
        if "import androidx.compose.ui.text.font.FontWeight" not in content and "import androidx.compose.ui.text.font.*" not in content:
            print(f"{fpath} -> Missing FontWeight import")
            all_clean = False

    if ".clip(" in content:
        if "import androidx.compose.ui.draw.clip" not in content and "import androidx.compose.ui.draw.*" not in content:
            print(f"{fpath} -> Missing clip import")
            all_clean = False

    if ".asImageBitmap()" in content:
        if "import androidx.compose.ui.graphics.asImageBitmap" not in content and "import androidx.compose.ui.graphics.*" not in content:
            print(f"{fpath} -> Missing asImageBitmap import")
            all_clean = False

    if ".verticalScroll(" in content:
        if "import androidx.compose.foundation.verticalScroll" not in content and "import androidx.compose.foundation.*" not in content:
            print(f"{fpath} -> Missing verticalScroll import")
            all_clean = False

    # Check that LocalSchoolBranding.current is never called inside onClick = { ... }
    # A composable invocation inside onClick lambda causes runtime compile error
    matches = re.finditer(r'onClick\s*=\s*\{([^}]+)\}', content)
    for m in matches:
        body = m.group(1)
        if "LocalSchoolBranding.current" in body:
            print(f"{fpath} -> ERROR: LocalSchoolBranding.current called inside onClick lambda!")
            all_clean = False
        if "LocalContext.current" in body:
            print(f"{fpath} -> ERROR: LocalContext.current called inside onClick lambda!")
            all_clean = False

if all_clean:
    print("ALL 17 MODIFIED CORE FILES PASSED STRICT DEEP AUDIT!")
