package pk.edunexa.app.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

enum class PortalRole(val value: String, val label: String) {
    OWNER("owner", "Owner"),
    ADMIN("admin", "Admin"),
    TEACHER("teacher", "Teacher"),
    STUDENT("student", "Student");

    companion object {
        fun fromValue(value: String?): PortalRole? =
            entries.firstOrNull { it.value == value?.trim()?.lowercase() }
    }
}

data class PortalSession(
    val uid: String,
    val name: String,
    val email: String,
    val role: PortalRole,
    val schoolId: String,
    val profileId: String? = null,
    val idNumber: String = "",
    val subtitle: String = "",
    val isClassIncharge: Boolean = false,
    val inchargeClassId: String? = null,
    val inchargeSectionId: String? = null,
    val inchargeAcademicSession: String? = null,
    val isPlatformOwner: Boolean = false
)

class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun restoreSession(onResult: (Result<PortalSession?>) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            onResult(Result.success(null))
            return
        }

        loadSession(currentUser.uid, expectedRole = null) { result ->
            if (result.isFailure) {
                auth.signOut()
                onResult(Result.failure(result.exceptionOrNull() ?: IllegalStateException("Session invalid.")))
            } else {
                onResult(Result.success(result.getOrNull()))
            }
        }
    }

    fun signIn(
        role: PortalRole,
        identifier: String,
        password: String,
        onResult: (Result<PortalSession>) -> Unit
    ) {
        val email = resolveLoginEmail(role, identifier)
        if (email == null) {
            onResult(Result.failure(IllegalArgumentException("Enter your full portal email address.")))
            return
        }

        if (password.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Enter your password.")))
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { credential ->
                val uid = credential.user?.uid
                if (uid.isNullOrBlank()) {
                    auth.signOut()
                    onResult(Result.failure(IllegalStateException("Firebase user ID missing.")))
                    return@addOnSuccessListener
                }

                loadSession(uid, expectedRole = role) { result ->
                    if (result.isFailure) auth.signOut()
                    onResult(result)
                }
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(IllegalStateException(authMessage(error), error)))
            }
    }

    fun signOut() {
        auth.signOut()
        pk.edunexa.app.notifications.clearStudentActivityNotifications()
    }

    private fun loadSession(uid:String,expectedRole:PortalRole?,onResult:(Result<PortalSession>)->Unit) {
        val current=auth.currentUser
        if(current==null || current.uid!=uid) { onResult(Result.failure(IllegalStateException("Sign in again.")));return }
        current.getIdToken(true).addOnSuccessListener { token ->
            loadSessionAccount(uid,expectedRole,token.claims["platformOwner"]==true) { result ->
                val session=result.getOrNull()
                if(session==null || session.role == PortalRole.OWNER) onResult(result)
                else db.collection("schools").document(session.schoolId).get(Source.SERVER).addOnSuccessListener { school ->
                    if(!school.exists()) onResult(Result.failure(IllegalStateException("Linked school was not found.")))
                    else if(school.getBoolean("portalAccessEnabled")==false && !session.isPlatformOwner) onResult(Result.failure(IllegalStateException("This school's portal access is suspended. Contact the portal Owner.")))
                    else onResult(result)
                }.addOnFailureListener { onResult(Result.failure(IllegalStateException("Could not verify school access. Check your connection or contact the portal Owner.",it))) }
            }
        }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    private fun loadSessionAccount(
        uid: String,
        expectedRole: PortalRole?,
        platformOwner: Boolean,
        onResult: (Result<PortalSession>) -> Unit
    ) {
        db.collection("users")
            .document(uid)
            .get(Source.SERVER)
            .addOnSuccessListener { account ->
                if (!account.exists()) {
                    onResult(Result.failure(IllegalStateException("Firestore user profile missing.")))
                    return@addOnSuccessListener
                }

                if (account.getBoolean("active") != true || account.getBoolean("portalDisabled") == true) {
                    onResult(Result.failure(IllegalStateException("This account is inactive.")))
                    return@addOnSuccessListener
                }

                val storedRole = account.getString("role")
                // A server-issued claim is required; a profile field never grants Owner access.
                val verifiedOwner = pk.edunexa.app.operations.OwnerPolicy.allowed(
                    uid, uid, mapOf("platformOwner" to platformOwner), account.data.orEmpty())
                if (PortalRole.fromValue(storedRole) == PortalRole.OWNER && !verifiedOwner) {
                    onResult(Result.failure(IllegalStateException("Owner permission could not be verified.")))
                    return@addOnSuccessListener
                }
                val role = if (verifiedOwner) PortalRole.OWNER else PortalRole.fromValue(storedRole)
                if (role == null) {
                    onResult(Result.failure(IllegalStateException("The account role is invalid.")))
                    return@addOnSuccessListener
                }

                if (expectedRole != null && role != expectedRole && !(role == PortalRole.OWNER && expectedRole == PortalRole.ADMIN)) {
                    onResult(
                        Result.failure(
                            IllegalStateException(
                                "This account belongs to the " + role.label + " portal, but you selected the " +
                                    expectedRole.label + " portal."
                            )
                        )
                    )
                    return@addOnSuccessListener
                }

                val schoolId = account.cleanString("schoolId")
                if (schoolId.isBlank() && role != PortalRole.OWNER) {
                    onResult(Result.failure(IllegalStateException("The account has no linked school.")))
                    return@addOnSuccessListener
                }

                val email = auth.currentUser?.email.orEmpty()

                when (role) {
                    PortalRole.OWNER -> onResult(Result.success(PortalSession(
                        uid = uid, name = account.cleanString("name").ifBlank { "Platform Owner" },
                        email = email, role = PortalRole.OWNER, schoolId = "",
                        idNumber = "OWNER", subtitle = "EduNexa Platform", isPlatformOwner = true
                    )))
                    PortalRole.ADMIN -> {
                        val session = PortalSession(
                            uid = uid,
                            name = account.cleanString("name").ifBlank { "School Administrator" },
                            email = email,
                            role = role,
                            schoolId = schoolId,
                            idNumber = "ADMIN",
                            subtitle = "School Administration",
                            isPlatformOwner = platformOwner
                        )
                        onResult(Result.success(session))
                    }

                    PortalRole.TEACHER -> {
                        val profileId = account.cleanString("profileId")
                        if (profileId.isBlank()) {
                            onResult(Result.failure(IllegalStateException("The account has no linked teacher profile.")))
                            return@addOnSuccessListener
                        }
                        loadTeacher(uid, email, schoolId, profileId, onResult)
                    }

                    PortalRole.STUDENT -> {
                        val profileId = account.cleanString("profileId")
                        if (profileId.isBlank()) {
                            onResult(Result.failure(IllegalStateException("The account has no linked student profile.")))
                            return@addOnSuccessListener
                        }
                        loadStudent(uid, email, schoolId, profileId, onResult)
                    }
                }
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(IllegalStateException(firestoreMessage(error), error)))
            }
    }

    private fun loadTeacher(
        uid: String,
        email: String,
        schoolId: String,
        profileId: String,
        onResult: (Result<PortalSession>) -> Unit
    ) {
        db.collection("schools")
            .document(schoolId)
            .collection("teachers")
            .document(profileId)
            .get(Source.SERVER)
            .addOnSuccessListener { teacher ->
                if (!teacher.exists()) {
                    onResult(Result.failure(IllegalStateException("The linked teacher record was not found.")))
                    return@addOnSuccessListener
                }

                if (!teacher.cleanString("status").equals("Active", ignoreCase = true)) {
                    onResult(Result.failure(IllegalStateException("The teacher account is inactive.")))
                    return@addOnSuccessListener
                }

                val inchargeClassId = teacher.cleanString("inchargeClassId").ifBlank { null }
                val inchargeSectionId = teacher.cleanString("inchargeSectionId").ifBlank { null }
                val isIncharge = inchargeClassId != null && inchargeSectionId != null

                onResult(
                    Result.success(
                        PortalSession(
                            uid = uid,
                            name = teacher.cleanString("name").ifBlank { "Teacher" },
                            email = email,
                            role = PortalRole.TEACHER,
                            schoolId = schoolId,
                            profileId = profileId,
                            idNumber = teacher.cleanString("employeeId"),
                            subtitle = if (isIncharge) "Teacher · Class Incharge" else "Faculty Workspace",
                            isClassIncharge = isIncharge,
                            inchargeClassId = inchargeClassId,
                            inchargeSectionId = inchargeSectionId,
                            inchargeAcademicSession =
                                teacher.cleanString("inchargeAcademicSession").ifBlank { null }
                        )
                    )
                )
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(IllegalStateException(firestoreMessage(error), error)))
            }
    }

    private fun loadStudent(
        uid: String,
        email: String,
        schoolId: String,
        profileId: String,
        onResult: (Result<PortalSession>) -> Unit
    ) {
        db.collection("schools")
            .document(schoolId)
            .collection("students")
            .document(profileId)
            .get(Source.SERVER)
            .addOnSuccessListener { student ->
                if (!student.exists()) {
                    onResult(Result.failure(IllegalStateException("The linked student record was not found.")))
                    return@addOnSuccessListener
                }

                if (student.cleanString("status").equals("Inactive", ignoreCase = true)) {
                    onResult(Result.failure(IllegalStateException("The student account is inactive.")))
                    return@addOnSuccessListener
                }

                val className = student.cleanString("className")
                val section = student.cleanString("section")
                val classSection = listOf(className, section)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")

                onResult(
                    Result.success(
                        PortalSession(
                            uid = uid,
                            name = student.cleanString("fullName").ifBlank { "Student" },
                            email = email,
                            role = PortalRole.STUDENT,
                            schoolId = schoolId,
                            profileId = profileId,
                            idNumber = student.cleanString("admissionNo"),
                            subtitle = classSection.ifBlank { "Student Workspace" }
                        )
                    )
                )
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(IllegalStateException(firestoreMessage(error), error)))
            }
    }

    private fun resolveLoginEmail(role: PortalRole, identifier: String): String? {
        val raw = identifier.trim().lowercase()
        if (raw.isBlank()) return null
        if (raw.contains("@")) return raw

        // Registration numbers repeat between schools. Only the school-issued full email is unambiguous.
        return null
    }

    private fun DocumentSnapshot.cleanString(field: String): String =
        getString(field)?.trim().orEmpty()

    private fun authMessage(error: Exception): String {
        val code = (error as? FirebaseAuthException)?.errorCode.orEmpty()
        return when (code) {
            "ERROR_INVALID_CREDENTIAL",
            "ERROR_WRONG_PASSWORD",
            "ERROR_USER_NOT_FOUND",
            "ERROR_INVALID_EMAIL" -> "The email, ID or password is incorrect."
            "ERROR_USER_DISABLED" -> "This account is disabled."
            "ERROR_TOO_MANY_REQUESTS" -> "Too many failed attempts. Please try again later."
            "ERROR_NETWORK_REQUEST_FAILED" -> "Check your internet connection."
            else -> if (code.isNotBlank()) "Login failed (" + code + ")." else "Login failed."
        }
    }

    private fun firestoreMessage(error: Exception): String {
        val message = error.message.orEmpty()
        return if (message.contains("PERMISSION_DENIED", ignoreCase = true)) {
            "Access was denied. Check your account permissions."
        } else {
            "Could not load the school profile. Check your internet connection and try again."
        }
    }
}


