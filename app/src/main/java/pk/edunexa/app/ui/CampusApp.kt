package pk.edunexa.app.ui
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import pk.edunexa.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pk.edunexa.app.auth.FirebaseAuthRepository
import pk.edunexa.app.auth.PortalRole
import pk.edunexa.app.auth.PortalSession

private sealed interface AppAuthState {
    data object Checking : AppAuthState
    data class SignedOut(
        val error: String? = null,
        val busy: Boolean = false
    ) : AppAuthState
    data class SignedIn(val session: PortalSession) : AppAuthState
}

@Composable
fun CampusApp(notificationOpen: Int = 0) {
    if (!pk.edunexa.app.BuildConfig.BACKEND_CONFIGURED) {
        CampusSetupScreen()
        return
    }
    val repository = remember { FirebaseAuthRepository() }
    var authState by remember { mutableStateOf<AppAuthState>(AppAuthState.Checking) }

    LaunchedEffect(Unit) {
        repository.restoreSession { result ->
            authState = result.fold(
                onSuccess = { session ->
                    if (session == null) AppAuthState.SignedOut()
                    else AppAuthState.SignedIn(session)
                },
                onFailure = {
                    AppAuthState.SignedOut(
                        error = "Your saved session could not be verified. Sign in again."
                    )
                }
            )
        }
    }

    val branding = rememberSchoolBranding((authState as? AppAuthState.SignedIn)?.session)
    androidx.compose.runtime.CompositionLocalProvider(LocalSchoolBranding provides branding) {
    MaterialTheme(colorScheme = schoolColorScheme(branding)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (val state = authState) {
                    AppAuthState.Checking -> LoadingScreen()

                    is AppAuthState.SignedOut -> Column(modifier = Modifier.fillMaxSize()) {
                        BrandHeader()
                        LoginScreen(
                            busy = state.busy,
                            error = state.error,
                            onLogin = { role, identifier, password ->
                                authState = AppAuthState.SignedOut(busy = true)
                                repository.signIn(role, identifier, password) { result ->
                                    authState = result.fold(
                                        onSuccess = { AppAuthState.SignedIn(it) },
                                        onFailure = {
                                            AppAuthState.SignedOut(
                                                error = it.message ?: "Login failed."
                                            )
                                        }
                                    )
                                }
                            }
                        )
                    }

                    is AppAuthState.SignedIn -> {
                        if (pk.edunexa.app.BuildConfig.ACTIVITY_PUSH_ENABLED && state.session.role != PortalRole.OWNER) AttendancePushSetup(state.session)
                        var showLogoutConfirm by rememberSaveable { mutableStateOf(false) }
                        val logout = { showLogoutConfirm = true }

                        if (showLogoutConfirm) {
                            AlertDialog(
                                onDismissRequest = { showLogoutConfirm = false },
                                title = { Text("Log Out?", fontWeight = FontWeight.Bold) },
                                text = { Text("Are you sure you want to log out of ${LocalSchoolBranding.current.schoolName}?") },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            showLogoutConfirm = false
                                            repository.signOut()
                                            authState = AppAuthState.SignedOut()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Log Out")
                                    }
                                },
                                dismissButton = {
                                    OutlinedButton(onClick = { showLogoutConfirm = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        PortalSessionGuard(state.session) {
                            repository.signOut()
                            authState = AppAuthState.SignedOut(error = "Portal access changed. Sign in again or contact the school Admin.")
                        }

                        if (state.session.role == PortalRole.OWNER) {
                            OwnerDashboardScreen(state.session, logout)
                        } else if (state.session.role == PortalRole.ADMIN) {
                            AdminDashboardScreen(
                                session = state.session,
                                onLogout = logout
                            )
                        } else if (state.session.role == PortalRole.TEACHER) {
                            TeacherPortalScreen(state.session, logout)
                        } else {
                            StudentPortalScreen(state.session, logout, notificationOpen)
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun LoginScreen(
    busy: Boolean,
    error: String?,
    onLogin: (PortalRole, String, String) -> Unit
) {
    var role by rememberSaveable { mutableStateOf(PortalRole.ADMIN) }
    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = LocalSchoolBranding.current.schoolName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Text(
            text = LocalSchoolBranding.current.campus,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "EduNexa Portal Login",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Select your role and sign in with your assigned account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PortalRole.entries.filter { it != PortalRole.OWNER }.forEach { item ->
                        val selected = role == item
                        if (selected) {
                            Button(
                                onClick = {
                                    role = item
                                    identifier = ""
                                    password = ""
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                enabled = !busy
                            ) {
                                Text(
                                    text = item.label,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    role = item
                                    identifier = ""
                                    password = ""
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                enabled = !busy
                            ) {
                                Text(
                                    text = item.label,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                val focusManager = LocalFocusManager.current
                var forgotPasswordOpen by rememberSaveable { mutableStateOf(false) }
                var forgotPasswordEmail by rememberSaveable { mutableStateOf("") }
                var forgotPasswordBusy by rememberSaveable { mutableStateOf(false) }
                var forgotPasswordNotice by rememberSaveable { mutableStateOf<String?>(null) }
                var forgotPasswordError by rememberSaveable { mutableStateOf<String?>(null) }

                if (forgotPasswordOpen) {
                    AlertDialog(
                        onDismissRequest = { if (!forgotPasswordBusy) forgotPasswordOpen = false },
                        title = { Text("Reset Password", fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Enter your account email address. Google will send you a password reset link.", style = MaterialTheme.typography.bodyMedium)
                                OutlinedTextField(
                                    value = forgotPasswordEmail,
                                    onValueChange = { forgotPasswordEmail = it; forgotPasswordError = null },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !forgotPasswordBusy,
                                    singleLine = true,
                                    label = { Text("Email Address") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done)
                                )
                                forgotPasswordError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                forgotPasswordNotice?.let { Text(it, color = Color(0xFF15803D), style = MaterialTheme.typography.bodySmall) }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val clean = forgotPasswordEmail.trim().lowercase()
                                    if (!clean.contains("@")) {
                                        forgotPasswordError = "Enter a valid email address."
                                        return@Button
                                    }
                                    forgotPasswordBusy = true
                                    forgotPasswordError = null
                                    forgotPasswordNotice = null
                                    FirebaseAuth.getInstance().sendPasswordResetEmail(clean)
                                        .addOnSuccessListener {
                                            forgotPasswordBusy = false
                                            forgotPasswordNotice = "Reset link sent! Please check your email inbox."
                                        }
                                        .addOnFailureListener { ex ->
                                            forgotPasswordBusy = false
                                            forgotPasswordError = ex.message ?: "Could not send password reset email."
                                        }
                                },
                                enabled = !forgotPasswordBusy && forgotPasswordEmail.isNotBlank()
                            ) {
                                Text(if (forgotPasswordBusy) "Sending..." else "Send Reset Link")
                            }
                        },
                        dismissButton = {
                            OutlinedButton(
                                onClick = { forgotPasswordOpen = false },
                                enabled = !forgotPasswordBusy
                            ) {
                                Text("Close")
                            }
                        }
                    )
                }

                OutlinedTextField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    label = {
                        Text(
                            when (role) {
                                PortalRole.OWNER -> "Owner Email"
                                PortalRole.ADMIN -> "Admin Email"
                                PortalRole.TEACHER -> "Teacher Portal Email"
                                PortalRole.STUDENT -> "Student Portal Email"
                            }
                        )
                    },
                    supportingText = {
                        Text(
                            when (role) {
                                PortalRole.OWNER -> "Enter your Owner email address"
                                PortalRole.ADMIN -> "Enter your Admin email address"
                                PortalRole.TEACHER -> "Use the full email supplied by your school"
                                PortalRole.STUDENT -> "Use the full email supplied by your school"
                            }
                        )
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (!busy && identifier.isNotBlank() && password.isNotBlank()) {
                                onLogin(role, identifier.trim(), password)
                            }
                        }
                    ),
                    label = { Text("Password") },
                    visualTransformation =
                        if (showPassword) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(
                            onClick = { showPassword = !showPassword },
                            enabled = !busy
                        ) {
                            Text(if (showPassword) "Hide" else "Show")
                        }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            forgotPasswordEmail = identifier.trim()
                            forgotPasswordNotice = null
                            forgotPasswordError = null
                            forgotPasswordOpen = true
                        },
                        enabled = !busy
                    ) {
                        Text("Forgot Password?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }

                if (!error.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { onLogin(role, identifier.trim(), password) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && identifier.isNotBlank() && password.isNotBlank()
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Sign In")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Developed by Muhammad Arfat",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.60f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun VerifiedPortalScreen(
    session: PortalSession,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = "Login Successful",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = session.role.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (session.subtitle.isNotBlank()) {
                    Text(
                        text = session.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                SessionLine("School", session.schoolId)
                if (session.idNumber.isNotBlank()) {
                    SessionLine(
                        when (session.role) {
                            PortalRole.OWNER -> "ID"
                            PortalRole.ADMIN -> "ID"
                            PortalRole.TEACHER -> "Employee ID"
                            PortalRole.STUDENT -> "Admission No."
                        },
                        session.idNumber
                    )
                }
                if (session.email.isNotBlank()) {
                    SessionLine("Email", session.email)
                }
                if (session.role == PortalRole.TEACHER && session.isClassIncharge) {
                    SessionLine("Class Incharge", "Yes")
                }

                Spacer(modifier = Modifier.height(22.dp))
                Text(
                    text = "Your account and school profile have been verified.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                )

                Spacer(modifier = Modifier.height(18.dp))
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Logout")
                }
            }
        }
    }
}

@Composable
private fun SessionLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun LoadingScreen() {
    SchoolOpeningScreen()
}

@Composable
private fun BrandHeader(schoolId: String? = null) {
    val schoolName = LocalSchoolBranding.current.schoolName
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary
    ) {
        Text(
            text = schoolName,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}



