package pk.edunexa.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import pk.edunexa.app.students.StudentClassOption
import pk.edunexa.app.students.StudentDirectoryItem
import pk.edunexa.app.students.StudentDirectoryRepository
import java.io.ByteArrayOutputStream
import java.util.Locale

private val EditorInk: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.primary
private val EditorGold: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.secondary
private val EditorSoft = Color(0xFFF8FAFC)
private val EditorBorder = Color(0xFFE2E8F0)
private val EditorMuted = Color(0xFF64748B)
private val EditorRed = Color(0xFFDC2626)

@Composable
fun StudentEditorDialog(
    schoolId: String,
    activeSessionId: String,
    repository: StudentDirectoryRepository,
    initial: StudentDirectoryItem?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val draftUid=com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val draftStore=remember(draftUid,schoolId,initial?.id){pk.edunexa.app.workflow.LocalDraftStore(context,draftUid,schoolId,"student",initial?.id ?: "new")}
    val fingerprint=initial?.let { listOf(it.fullName,it.rollNo,it.classId,it.sectionId,it.status,it.guardianPhone,it.monthlyFee,it.annualFund,it.otherFunds,it.portalEmail).joinToString("|") }.orEmpty()
    val previousDraft=remember(draftUid,schoolId,initial?.id){draftStore.load()}
    val recovered=remember(previousDraft,fingerprint){previousDraft.takeIf{it["origin"].orEmpty()==fingerprint}.orEmpty()}
    val draftStudentId = remember(initial?.id, schoolId) { initial?.id ?: recovered["draftStudentId"] ?: repository.newStudentId(schoolId) }
    var generatedLogin by remember { mutableStateOf<pk.edunexa.app.auth.GeneratedLogin?>(null) }
    var schoolShortName by remember(schoolId) { mutableStateOf("") }
    var loginComplete by remember { mutableStateOf(false) }
    var classes by remember { mutableStateOf<List<StudentClassOption>>(emptyList()) }
    var loadingOptions by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var fullName by remember(initial) { mutableStateOf(recovered["fullName"] ?: (initial?.fullName.orEmpty())) }
    var admissionNo by remember(initial) { mutableStateOf(recovered["admissionNo"] ?: (initial?.admissionNo.orEmpty())) }
    var registering by remember { mutableStateOf(initial == null) }
    var registrationRetry by remember { mutableStateOf(0) }
    LaunchedEffect(schoolId, draftStudentId, registrationRetry) {
        if(initial == null) {
            registering = true
            pk.edunexa.app.students.RegistrationRepository().reserve(schoolId, draftStudentId) { result ->
                registering = false
                result.onSuccess { if (admissionNo.isBlank()) admissionNo = it }.onFailure { error = it.message ?: "Could not reserve registration number." }
            }
        }
    }
    var rollNo by remember(initial) { mutableStateOf(recovered["rollNo"] ?: (initial?.rollNo.orEmpty())) }
    var admissionDate by remember(initial) { mutableStateOf(recovered["admissionDate"] ?: (initial?.admissionDate.orEmpty())) }
    var gender by remember(initial) { mutableStateOf(recovered["gender"] ?: (initial?.gender?.ifBlank { "Male" } ?: "Male")) }
    var dateOfBirth by remember(initial) { mutableStateOf(recovered["dateOfBirth"] ?: (initial?.dateOfBirth.orEmpty())) }
    var bloodGroup by remember(initial) { mutableStateOf(recovered["bloodGroup"] ?: (initial?.bloodGroup.orEmpty())) }
    var bForm by remember(initial) { mutableStateOf(recovered["bForm"] ?: (initial?.bForm.orEmpty())) }
    var fatherName by remember(initial) { mutableStateOf(recovered["fatherName"] ?: (initial?.fatherName.orEmpty())) }
    var fatherOccupation by remember(initial) { mutableStateOf(recovered["fatherOccupation"] ?: (initial?.fatherOccupation.orEmpty())) }
    var fatherCnic by remember(initial) { mutableStateOf(recovered["fatherCnic"] ?: (initial?.fatherCnic.orEmpty())) }
    var guardianPhone by remember(initial) { mutableStateOf(recovered["guardianPhone"] ?: (initial?.guardianPhone.orEmpty())) }
    var emergencyPhone by remember(initial) { mutableStateOf(recovered["emergencyPhone"] ?: (initial?.emergencyPhone.orEmpty())) }
    var address by remember(initial) { mutableStateOf(recovered["address"] ?: (initial?.address.orEmpty())) }
    var monthlyFee by remember(initial) { mutableStateOf(recovered["monthlyFee"] ?: (initial?.monthlyFee?.takeIf { it > 0 }?.toInt()?.toString().orEmpty())) }
    var annualFund by remember(initial) { mutableStateOf(recovered["annualFund"] ?: (initial?.annualFund?.toInt()?.toString() ?: "0")) }
    var otherFunds by remember(initial) { mutableStateOf(recovered["otherFunds"] ?: (initial?.otherFunds?.toInt()?.toString() ?: "0")) }
    var status by remember(initial) { mutableStateOf(recovered["status"] ?: (initial?.status?.ifBlank { "Active" } ?: "Active")) }
    var remarks by remember(initial) { mutableStateOf(recovered["remarks"] ?: (initial?.remarks.orEmpty())) }
    var selectedClassId by remember(initial) { mutableStateOf(recovered["selectedClassId"] ?: (initial?.classId.orEmpty())) }
    var selectedClassName by remember(initial) { mutableStateOf(recovered["selectedClassName"] ?: (initial?.className.orEmpty())) }
    var selectedSectionId by remember(initial) { mutableStateOf(recovered["selectedSectionId"] ?: (initial?.sectionId.orEmpty())) }
    var selectedSectionName by remember(initial) { mutableStateOf(recovered["selectedSectionName"] ?: (initial?.section.orEmpty())) }
    var photo by remember(initial) { mutableStateOf(recovered["photo"]?.takeIf{it.isNotBlank()} ?: initial?.profilePhoto) }
    var portalEmail by remember(initial) { mutableStateOf(recovered["portalEmail"] ?: (initial?.portalEmail.orEmpty())) }
    val draftValues=mapOf("draftStudentId" to draftStudentId,"origin" to fingerprint,"photo" to photo.orEmpty(),"fullName" to fullName,"admissionNo" to admissionNo,"rollNo" to rollNo,"admissionDate" to admissionDate,"gender" to gender,"dateOfBirth" to dateOfBirth,"bloodGroup" to bloodGroup,"bForm" to bForm,"fatherName" to fatherName,"fatherOccupation" to fatherOccupation,"fatherCnic" to fatherCnic,"guardianPhone" to guardianPhone,"emergencyPhone" to emergencyPhone,"address" to address,"monthlyFee" to monthlyFee,"annualFund" to annualFund,"otherFunds" to otherFunds,"status" to status,"remarks" to remarks,"selectedClassId" to selectedClassId,"selectedClassName" to selectedClassName,"selectedSectionId" to selectedSectionId,"selectedSectionName" to selectedSectionName,"portalEmail" to portalEmail)
    LaunchedEffect(draftValues,loginComplete) {
        if(loginComplete) draftStore.clear()
        else { kotlinx.coroutines.delay(350); try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){draftStore.save(draftValues)} } catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error="Draft could not be saved on this phone. Keep this form open until you save."} }
    }

    var credentialsOpen by remember { mutableStateOf(false) }

    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val croppedUri = result.uriContent
            if (croppedUri != null) {
                runCatching { compressPhoto(context, croppedUri) }
                    .onSuccess { photo = it; error = null }
                    .onFailure { error = "Could not save the cropped photo. Try another image." }
            }
        } else if (result.error != null) {
            error = "Could not crop the photo: " + (result.error?.localizedMessage ?: "Unknown error")
        }
    }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            cropLauncher.launch(
                CropImageContractOptions(
                    uri = uri,
                    cropImageOptions = CropImageOptions(
                        guidelines = CropImageView.Guidelines.ON,
                        fixAspectRatio = true,
                        aspectRatioX = 1,
                        aspectRatioY = 1,
                        outputCompressFormat = Bitmap.CompressFormat.JPEG,
                        outputCompressQuality = 90,
                        activityTitle = "Crop Student Photo",
                        cropMenuCropButtonTitle = "Use Photo"
                    )
                )
            )
        }
    }

    LaunchedEffect(schoolId) {
        loadingOptions = true
        repository.loadLoginShortName(schoolId) { result ->
            result.onSuccess { schoolShortName = it }.onFailure { error = "Could not load School Short Form. Close and retry." }
        }
        repository.loadAcademicOptions(schoolId) { result ->
            result.onSuccess { loaded ->
                classes = loaded
                if (loaded.isNotEmpty()) {
                    val matchedClass = loaded.firstOrNull {
                        (selectedClassId.isNotBlank() && it.id == selectedClassId) ||
                            it.name.equals(selectedClassName, true)
                    } ?: loaded.first()

                    selectedClassId = matchedClass.id
                    selectedClassName = matchedClass.name

                    val matchedSection = matchedClass.sections.firstOrNull {
                        (selectedSectionId.isNotBlank() && it.id == selectedSectionId) ||
                            it.name.equals(selectedSectionName, true)
                    } ?: matchedClass.sections.firstOrNull()

                    selectedSectionId = matchedSection?.id.orEmpty()
                    selectedSectionName = matchedSection?.name.orEmpty()
                }
                loadingOptions = false
            }.onFailure {
                loadingOptions = false
                error = it.message ?: "Could not load academic settings."
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.92f),
            shape = RoundedCornerShape(22.dp),
            color = Color.White
        ) {
            Column(Modifier.fillMaxSize()) {
                Surface(color = EditorInk) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp)) {
                        Text(
                            if (initial == null) "Add Student" else "Edit Student",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "EduNexa · Live Firebase Record",
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.sp
                        )
                    }
                }

                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    if (!error.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFFF1F2),
                            border = BorderStroke(1.dp, Color(0xFFFECACA))
                        ) {
                            Text(error.orEmpty(), Modifier.padding(10.dp), color = EditorRed, fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    SectionTitle("Student Photo")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(74.dp),
                            shape = RoundedCornerShape(17.dp),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                            border = BorderStroke(1.dp, Color(0xFFF7D7A1))
                        ) {
                            val bitmap = remember(photo) { decodeEditorPhoto(photo) }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = "Student photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        editorInitials(fullName),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = EditorGold
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            OutlinedButton(onClick = { photoLauncher.launch("image/*") }, enabled = !saving) {
                                Text(if (photo.isNullOrBlank()) "Choose Photo" else "Change Photo")
                            }
                            if (!photo.isNullOrBlank()) {
                                OutlinedButton(onClick = { photo = null }, enabled = !saving) {
                                    Text("Remove Photo", color = EditorRed)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(15.dp))
                    SectionTitle("Academic & Identification")
                    if(recovered.isNotEmpty()) { Text("Draft recovered from this phone. Review before saving.",color=EditorGold,fontSize=12.sp);TextButton(onClick={draftStore.clear();onDismiss()},enabled=!saving){Text("Discard saved draft")}}
                    Field("Student Full Name *", fullName, { fullName = it })
                    SelectField("Gender", gender, listOf("Male", "Female")) { gender = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            SelectField(
                                "Class",
                                selectedClassName.ifBlank { "Select" },
                                classes.map { it.name },
                                enabled = !loadingOptions
                            ) { name ->
                                val cls = classes.first { it.name == name }
                                selectedClassId = cls.id
                                selectedClassName = cls.name
                                val first = cls.sections.firstOrNull()
                                selectedSectionId = first?.id.orEmpty()
                                selectedSectionName = first?.name.orEmpty()
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            val sections = classes.firstOrNull { it.id == selectedClassId }?.sections.orEmpty()
                            SelectField(
                                "Section",
                                selectedSectionName.ifBlank { "Select" },
                                sections.map { it.name },
                                enabled = sections.isNotEmpty() && !loadingOptions
                            ) { name ->
                                val sec = sections.first { it.name == name }
                                selectedSectionId = sec.id
                                selectedSectionName = sec.name
                            }
                        }
                    }
                    if(initial == null) {
                        OutlinedTextField(admissionNo, { admissionNo = it.trim().uppercase(Locale.US) }, Modifier.fillMaxWidth(), enabled = !registering && !saving && generatedLogin == null, label = { Text("Registration No.") }, supportingText = { Text(if(registering) "Reserving next serial number…" else "Suggested automatically; edit before creating the login.") })
                        val suggestedEmail = runCatching { pk.edunexa.app.auth.StudentLoginIdentity.email(fullName, admissionNo, schoolShortName) }.getOrDefault("")
                        OutlinedTextField(portalEmail.ifBlank { suggestedEmail }, { portalEmail = it.trim().lowercase(Locale.US) }, Modifier.fillMaxWidth(), enabled = !saving && generatedLogin == null, singleLine = true, label = { Text("Login email") }, supportingText = { Text("Name + registration number. You may choose a different email.") })
                        if(!registering && admissionNo.isBlank()) TextButton(onClick = { registrationRetry += 1 }) { Text("Retry Registration") }
                    } else Field("Admission No. *", admissionNo, { admissionNo = it })
                    Field("Roll No. *", rollNo, { rollNo = it })
                    Field("Admission Date", admissionDate, { admissionDate = it }, "e.g. 15 Aug 2026")
                    Field("Date of Birth", dateOfBirth, { dateOfBirth = it }, "e.g. 28 Dec 2014")
                    SelectField(
                        "Blood Group",
                        bloodGroup.ifBlank { "Not specified" },
                        listOf("Not specified", "A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-")
                    ) { bloodGroup = if (it == "Not specified") "" else it }
                    Field("B-Form / CNIC", bForm, { bForm = it }, "XXXXX-XXXXXXX-X")

                    Spacer(Modifier.height(12.dp))
                    SectionTitle("Father / Guardian")
                    Field("Father's Name *", fatherName, { fatherName = it })
                    Field("Father's Occupation", fatherOccupation, { fatherOccupation = it })
                    Field("Father CNIC", fatherCnic, { fatherCnic = it }, "XXXXX-XXXXXXX-X")
                    Field("Guardian Mobile *", guardianPhone, { guardianPhone = it })
                    Field("Emergency Contact", emergencyPhone, { emergencyPhone = it })
                    Field("Address", address, { address = it }, minLines = 2)

                    Spacer(Modifier.height(12.dp))
                    SectionTitle("Fees & Status")
                    Field("Monthly Fee *", monthlyFee, { monthlyFee = it.filter { ch -> ch.isDigit() } })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            Field("Annual Fund", annualFund, { annualFund = it.filter { ch -> ch.isDigit() } })
                        }
                        Box(Modifier.weight(1f)) {
                            Field("Other Funds", otherFunds, { otherFunds = it.filter { ch -> ch.isDigit() } })
                        }
                    }
                    SelectField("Status", status, listOf("Active", "On Leave", "Inactive")) { status = it }
                    Field("Remarks", remarks, { remarks = it }, minLines = 3)

                    if (initial != null) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = EditorSoft,
                            border = BorderStroke(1.dp, EditorBorder)
                        ) {
                            Column(Modifier.padding(11.dp)) {
                                Text("PORTAL LOGIN", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = EditorMuted)
                                Text(
                                    if (looksCorruptPortalEmail(portalEmail))
                                        cleanPortalEmail(admissionNo, schoolId) + "  ·  repair pending"
                                    else portalEmail.ifBlank { if (initial == null) runCatching { pk.edunexa.app.auth.StudentLoginIdentity.email(fullName, admissionNo, schoolShortName) }.getOrDefault("Set School Short Form in School Settings") else cleanPortalEmail(admissionNo, schoolId) },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (looksCorruptPortalEmail(portalEmail)) EditorRed else EditorInk
                                )
                                Spacer(Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = { credentialsOpen = true },
                                    enabled = initial != null
                                ) {
                                    Text(if (initial == null) "Set Credentials" else "Edit Login Credentials")
                                }
                                Text(
                                    "Update login email or password for this student.",
                                    fontSize = 9.sp,
                                    color = EditorMuted
                                )
                            }
                        }
                    }
                }

                Surface(color = Color.White, shadowElevation = 8.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            enabled = !saving
                        ) { Text("Cancel") }
                        Button(
                            onClick = {
                                error = validateEditor(
                                    fullName = fullName,
                                    admissionNo = admissionNo,
                                    rollNo = rollNo,
                                    classId = selectedClassId,
                                    sectionId = selectedSectionId,
                                    fatherName = fatherName,
                                    guardianPhone = guardianPhone,
                                    monthlyFee = monthlyFee,
                                    bForm = bForm,
                                    fatherCnic = fatherCnic
                                )
                                if (error != null) return@Button

                                if (initial == null && generatedLogin == null) {
                                    val candidate = runCatching {
                                        pk.edunexa.app.auth.LoginGenerator.generate("student", schoolId, draftStudentId, admissionNo, fullName, schoolShortName, portalEmail)
                                    }
                                    if (candidate.isFailure) { error = candidate.exceptionOrNull()?.message; return@Button }
                                    generatedLogin = candidate.getOrThrow()
                                }
                                val id = draftStudentId
                                val item = StudentDirectoryItem(
                                    id = id,
                                    fullName = fullName.trim(),
                                    admissionNo = admissionNo.trim(),
                                    rollNo = rollNo.trim(),
                                    admissionDate = admissionDate.trim(),
                                    gender = gender,
                                    dateOfBirth = dateOfBirth.trim(),
                                    bloodGroup = bloodGroup,
                                    bForm = bForm.trim(),
                                    className = selectedClassName,
                                    section = selectedSectionName,
                                    classId = selectedClassId,
                                    sectionId = selectedSectionId,
                                    fatherName = fatherName.trim(),
                                    fatherOccupation = fatherOccupation.trim(),
                                    fatherCnic = fatherCnic.trim(),
                                    guardianPhone = guardianPhone.trim(),
                                    emergencyPhone = emergencyPhone.trim().ifBlank { guardianPhone.trim() },
                                    address = address.trim(),
                                    status = status,
                                    feeStatus = initial?.feeStatus ?: "No fee record",
                                    monthlyFee = monthlyFee.toDoubleOrNull() ?: 0.0,
                                    annualFund = annualFund.toDoubleOrNull() ?: 0.0,
                                    otherFunds = otherFunds.toDoubleOrNull() ?: 0.0,
                                    balanceDue = initial?.balanceDue ?: 0.0,
                                    portalEmail = if (initial == null) generatedLogin?.email.orEmpty() else portalEmail,
                                    remarks = remarks.trim(),
                                    isNewAdmission = initial?.isNewAdmission ?: true,
                                    profilePhoto = photo
                                )
                                if (initial == null && generatedLogin == null) {
                                    generatedLogin = pk.edunexa.app.auth.LoginGenerator.generate("student", schoolId, item.id, item.admissionNo, item.fullName, schoolShortName)
                                }
                                saving = true
                                repository.saveStudent(
                                    schoolId = schoolId,
                                    activeSessionId = activeSessionId,
                                    student = item,
                                    original = initial
                                ) { result ->
                                    result.onSuccess {
                                        if (initial == null) {
                                            pk.edunexa.app.auth.ProfileLoginRepository().create(schoolId, "student", item.id, generatedLogin!!) { loginResult ->
                                                saving = false
                                                loginResult.onSuccess { loginComplete = true }.onFailure { error = it.message ?: "Login creation failed. Retry saving." }
                                            }
                                        } else { saving = false; draftStore.clear();onSaved() }
                                    }.onFailure {
                                        saving = false
                                        error = it.message ?: "Could not save the student."
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !saving && !loadingOptions && !registering && admissionNo.isNotBlank()
                        ) {
                            if (saving) {
                                Text("Saving… ", fontSize = 11.sp)
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Text(if (initial == null) "Add Student" else "Save Changes")
                            }
                        }
                    }
                }
            }
        }
    }

    if (loginComplete && generatedLogin != null) {
        GeneratedLoginDialog(generatedLogin!!) { generatedLogin = null; loginComplete = false; onSaved() }
    }

    if (credentialsOpen && initial != null) {
        CredentialEditorDialog(
            student = initial,
            schoolId = schoolId,
            repository = repository,
            currentEmail = portalEmail,
            suggestedEmail = cleanPortalEmail(admissionNo, schoolId),
            onDismiss = { credentialsOpen = false },
            onSaved = { updatedEmail ->
                portalEmail = updatedEmail
                credentialsOpen = false
                error = null
            }
        )
    }
}

@Composable
private fun CredentialEditorDialog(
    student: StudentDirectoryItem,
    schoolId: String,
    repository: StudentDirectoryRepository,
    currentEmail: String,
    suggestedEmail: String,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    var email by remember(student.id, currentEmail) {
        mutableStateOf(
            if (looksCorruptPortalEmail(currentEmail)) suggestedEmail
            else currentEmail.ifBlank { suggestedEmail }
        )
    }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Edit Login Credentials", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = EditorInk)
                Text(student.fullName, fontSize = 10.sp, color = EditorGold, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))

                if (!error.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFFF1F2),
                        border = BorderStroke(1.dp, Color(0xFFFECACA))
                    ) {
                        Text(error.orEmpty(), Modifier.padding(9.dp), color = EditorRed, fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Login Email") },
                    singleLine = true
                )
                if (looksCorruptPortalEmail(currentEmail) && suggestedEmail.isNotBlank()) {
                    Text(
                        "Clean suggestion: $suggestedEmail",
                        fontSize = 9.sp,
                        color = EditorMuted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("New Password") },
                    placeholder = { Text("Blank = current password unchanged") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirm New Password") },
                    singleLine = true,
                    enabled = password.isNotBlank()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "The password is updated securely in Firebase Authentication and is never stored in the student record.",
                    fontSize = 9.sp,
                    color = EditorMuted
                )

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = !saving
                    ) { Text("Cancel") }

                    Button(
                        onClick = {
                            val clean = email.trim().lowercase(Locale.US)
                            error = when {
                                !clean.contains("@") -> "Enter a valid email address."
                                password.isNotBlank() && password.length < 6 -> "Password must contain at least 6 characters."
                                password != confirmPassword -> "Password confirmation does not match."
                                else -> null
                            }
                            if (error != null) return@Button

                            saving = true
                            repository.updateStudentCredentials(
                                schoolId = schoolId,
                                studentId = student.id,
                                email = clean,
                                newPassword = password
                            ) { result ->
                                result.onSuccess {
                                    saving = false
                                    onSaved(it)
                                }.onFailure {
                                    saving = false
                                    error = it.message ?: "Could not update login credentials."
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !saving
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else Text("Save Credentials")
                    }
                }
            }
        }
    }
}

private fun cleanPortalEmail(admissionNo: String, schoolId: String): String {
    val local = admissionNo.lowercase(Locale.US).filter { it.isLetterOrDigit() }
    return if (local.isBlank()) "" else pk.edunexa.app.auth.CampusLoginIdentity.email("student", schoolId, local)
}

private fun looksCorruptPortalEmail(email: String): Boolean {
    val value = email.trim().lowercase(Locale.US)
    if (value.isBlank()) return true
    val local = value.substringBefore("@", value)
    return value.length > 150 ||
        local.length > 64 ||
        local.contains("cloudstu") ||
        Regex("(cloud.*){2,}").containsMatchIn(local)
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = EditorInk,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
        label = { Text(label) },
        placeholder = if (placeholder.isBlank()) null else ({ Text(placeholder) }),
        singleLine = minLines == 1,
        minLines = minLines,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun SelectField(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean = true,
    onChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(bottom = 7.dp)) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = EditorMuted)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled
            ) {
                Text(value, modifier = Modifier.weight(1f), color = EditorInk)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun validateEditor(
    fullName: String,
    admissionNo: String,
    rollNo: String,
    classId: String,
    sectionId: String,
    fatherName: String,
    guardianPhone: String,
    monthlyFee: String,
    bForm: String,
    fatherCnic: String
): String? {
    if (fullName.isBlank()) return "Student full name is required."
    if (admissionNo.isBlank()) return "Admission number is required."
    if (rollNo.isBlank()) return "Roll number is required."
    if (classId.isBlank() || sectionId.isBlank()) return "Select a valid class and section."
    if (fatherName.isBlank()) return "Father or guardian name is required."
    if (guardianPhone.filter(Char::isDigit).length < 10) return "Enter a valid guardian mobile number."
    if (monthlyFee.isBlank()) return "Enter the monthly fee."
    if (bForm.isNotBlank() && bForm.filter(Char::isDigit).length != 13) {
        return "B-Form / CNIC must contain 13 digits, or be left blank."
    }
    if (fatherCnic.isNotBlank() && fatherCnic.filter(Char::isDigit).length != 13) {
        return "Father's CNIC must contain 13 digits, or be left blank."
    }
    return null
}

private fun compressPhoto(context: android.content.Context, uri: Uri): String {
    val original = context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Image stream unavailable" }
        requireNotNull(BitmapFactory.decodeStream(input)) { "Image decode failed" }
    }

    fun scaled(source: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= maxSide) return source
        val ratio = maxSide.toFloat() / longest.toFloat()
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    var working = scaled(original, 640)
    var quality = 72
    var bytes: ByteArray
    while (true) {
        val out = ByteArrayOutputStream()
        working.compress(Bitmap.CompressFormat.JPEG, quality, out)
        bytes = out.toByteArray()
        if (bytes.size <= 330_000 || quality <= 48) break
        quality -= 8
    }

    if (bytes.size > 420_000) {
        working = scaled(working, 480)
        val out = ByteArrayOutputStream()
        working.compress(Bitmap.CompressFormat.JPEG, 55, out)
        bytes = out.toByteArray()
    }

    require(bytes.size <= 500_000) { "Image too large" }
    return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
}

private fun decodeEditorPhoto(value: String?): androidx.compose.ui.graphics.ImageBitmap? {
    if (value.isNullOrBlank() || !value.startsWith("data:image")) return null
    return runCatching {
        val encoded = value.substringAfter("base64,", "")
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

private fun editorInitials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2)
        .mapNotNull { it.firstOrNull()?.uppercase(Locale.getDefault()) }
        .joinToString("").ifBlank { "S" }


