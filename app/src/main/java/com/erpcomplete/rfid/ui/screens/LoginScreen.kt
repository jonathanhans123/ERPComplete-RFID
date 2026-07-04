package com.erpcomplete.rfid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.erpcomplete.rfid.BuildConfig
import com.erpcomplete.rfid.R
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.data.remote.LoginRequest
import com.erpcomplete.rfid.data.remote.LoginResponse
import com.erpcomplete.rfid.ui.components.ErpPrimaryButton
import com.erpcomplete.rfid.ui.components.StatusBanner
import com.erpcomplete.rfid.ui.theme.IndigoDark
import com.erpcomplete.rfid.ui.theme.IndigoPrimary
import com.erpcomplete.rfid.ui.theme.SlateBackground
import com.erpcomplete.rfid.util.ApiErrorParser
import com.erpcomplete.rfid.util.AppLog
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(container: AppContainer, onLoginSuccess: (needsWorkspace: Boolean) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var twoFactorCode by remember { mutableStateOf("") }
    var needsTwoFactor by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
    )

    fun backToCredentials() {
        needsTwoFactor = false
        twoFactorCode = ""
        error = null
    }

    suspend fun finishLogin(trimmedEmail: String, body: LoginResponse, token: String) {
        val units = body.business_units?.map { it.toOption() } ?: emptyList()
        container.authStore.saveLogin(
            token = token,
            email = trimmedEmail,
            name = body.user?.name,
            businessUnits = units,
        )
        container.authStore.saveMobileInventoryPermissionsFromJson(body.mobile_permissions)
        val wsRes = container.api.listWorkspaces()
        if (!wsRes.isSuccessful) {
            error = ApiErrorParser.httpMessage(wsRes)
            return
        }
        val wsPayload = com.erpcomplete.rfid.util.WorkflowJson.envelopeWorkspacesPayload(wsRes)
        AppLog.i(
            "Login success — ${wsPayload.businessUnits.size} BU(s), " +
                "${wsPayload.warehouses.size} warehouse(s)",
        )
        when {
            wsPayload.businessUnits.isEmpty() ->
                error = context.getString(R.string.login_no_business_unit)
            wsPayload.warehouses.isEmpty() ->
                error = context.getString(R.string.login_no_warehouse)
            wsPayload.businessUnits.size == 1 &&
                wsPayload.warehouses.size == 1 &&
                wsPayload.warehouses.first().teamId != null -> {
                container.authStore.saveWorkspace(wsPayload.warehouses.first())
                container.refreshMobilePermissions()
                onLoginSuccess(false)
            }
            else -> onLoginSuccess(true)
        }
    }

    fun submitLogin() {
        loading = true
        error = null
        scope.launch {
            val trimmedEmail = email.trim()
            val code = twoFactorCode.trim().ifBlank { null }
            AppLog.i("Login attempt for $trimmedEmail → ${BuildConfig.API_BASE_URL}")
            try {
                val response = container.api.login(
                    LoginRequest(
                        email = trimmedEmail,
                        password = password,
                        two_factor_code = code,
                    ),
                )
                AppLog.api("POST", "auth/login", response.code())
                if (ApiErrorParser.isTwoFactorRequired(response)) {
                    needsTwoFactor = true
                    twoFactorCode = ""
                    error = null
                    return@launch
                }
                val body = response.body()
                val token = body?.access_token
                if (!response.isSuccessful || token.isNullOrBlank()) {
                    error = ApiErrorParser.httpMessage(response)
                    AppLog.w("Login failed: HTTP ${response.code()} — $error")
                    return@launch
                }
                finishLogin(trimmedEmail, body, token)
            } catch (e: Exception) {
                error = ApiErrorParser.networkMessage(e)
                AppLog.e("Login network error", e)
            } finally {
                loading = false
            }
        }
    }

    val canSubmit = if (needsTwoFactor) {
        twoFactorCode.isNotBlank()
    } else {
        email.isNotBlank() && password.isNotBlank()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(listOf(IndigoPrimary, IndigoDark)),
                        RoundedCornerShape(24.dp),
                    )
                    .padding(28.dp),
            ) {
                Column {
                    Icon(
                        if (needsTwoFactor) Icons.Default.Security else Icons.Default.RssFeed,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.login_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(Modifier.padding(24.dp)) {
                    if (needsTwoFactor) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { backToCredentials() }, enabled = !loading) {
                                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.login_two_factor_back))
                            }
                            Text(
                                stringResource(R.string.login_two_factor_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            stringResource(R.string.login_two_factor_subtitle, email.trim()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
                        )
                        OutlinedTextField(
                            value = twoFactorCode,
                            onValueChange = { value ->
                                twoFactorCode = value.filter { it.isDigit() }.take(8)
                            },
                            label = { Text(stringResource(R.string.login_two_factor_code)) },
                            leadingIcon = { Icon(Icons.Default.Pin, null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = {
                                focusManager.clearFocus()
                                if (canSubmit && !loading) submitLogin()
                            }),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                        )
                    } else {
                        Text(
                            stringResource(R.string.login_welcome),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(R.string.login_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
                        )

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text(stringResource(R.string.login_email)) },
                            leadingIcon = { Icon(Icons.Default.Email, null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next,
                            ),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                        )

                        Spacer(Modifier.height(14.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.login_password)) },
                            leadingIcon = { Icon(Icons.Default.Lock, null) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (passwordVisible) {
                                            stringResource(R.string.login_hide_password)
                                        } else {
                                            stringResource(R.string.login_show_password)
                                        },
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                focusManager.clearFocus()
                                if (canSubmit && !loading) submitLogin()
                            }),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                        )
                    }

                    error?.let {
                        Spacer(Modifier.height(14.dp))
                        StatusBanner(it, isError = true)
                    }

                    Spacer(Modifier.height(22.dp))

                    ErpPrimaryButton(
                        text = stringResource(
                            if (needsTwoFactor) R.string.login_two_factor_verify else R.string.login_sign_in,
                        ),
                        loading = loading,
                        enabled = canSubmit,
                        onClick = { submitLogin() },
                    )

                    if (needsTwoFactor) {
                        TextButton(
                            onClick = { backToCredentials() },
                            enabled = !loading,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text(stringResource(R.string.login_two_factor_back))
                        }
                    }
                }
            }

            if (BuildConfig.DEBUG) {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.login_api_debug, BuildConfig.API_BASE_URL),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
