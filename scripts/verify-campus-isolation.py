from pathlib import Path
import re
import json
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
gradle = (root / "app/build.gradle.kts").read_text()
assert 'applicationId = "pk.edunexa.app"' in gradle
assert 'namespace = "pk.edunexa.app"' in gradle
assert 'project["project_id"] != "al-nisa-school-portal"' in gradle
assert 'BACKEND_CONFIGURED' in gradle
config = json.loads((root / "app/google-services.json").read_text())
assert config["project_info"]["project_id"] == "edunexa-portal"
assert any(c["client_info"]["android_client_info"]["package_name"] == "pk.edunexa.app" for c in config["client"])
assert not (root / ".firebaserc").exists()
assert not list((root / "app/src").rglob("al_nisa_logo.webp"))
for source in (root / "app/src").rglob("*.kt"):
    content = source.read_text()
    assert "pk.edu.alnisa.school" not in content, source
    assert "alnisa.edu.pk" not in content, source
    assert '"Zafarwal"' not in content, source
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()
app = manifest.find("application")
ns = "{http://schemas.android.com/apk/res/android}"
assert app.attrib[ns + "allowBackup"] == "false"
main = (root / "app/src/main/java/pk/edunexa/app/ui/CampusApp.kt").read_text()
assert main.index("!pk.edunexa.app.BuildConfig.BACKEND_CONFIGURED") < main.index("FirebaseAuthRepository()")
print("Campus starter isolation checks passed.")

owner_ui = (root / "app/src/main/java/pk/edunexa/app/ui/OwnerPanelScreen.kt").read_text()
assert not re.search(r"fun\s+Any\?\.orEmpty\s*\(", owner_ui), "Do not shadow Kotlin orEmpty in Owner UI"
assert owner_ui.count("OwnerPolicy.displayText(") == 3
print("Owner card text conversion regression checks passed.")

assert (root / "app/src/main/res/drawable-nodpi/campus_launcher_foreground.png").exists()
assert not (root / "app/src/main/res/drawable/campus_launcher_foreground.xml").exists(), "Do not reintroduce self-referential campus_launcher_foreground.xml"
assert 'versionCode = 23' in gradle and 'versionName = "0.4.0"' in gradle
print("Launcher icon resource and version 0.4.0 checks passed.")
