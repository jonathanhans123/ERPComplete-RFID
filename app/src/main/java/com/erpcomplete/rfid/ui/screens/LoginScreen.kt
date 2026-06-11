package com.erpcomplete.rfid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RssFeed
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.erpcomplete.rfid.BuildConfig
import com.erpcomplete.rfid.data.AppContainer
import com.erpcomplete.rfid.data.remote.LoginRequest
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
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
    )

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
                        Icons.Default.RssFeed,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "ERPComplete RFID",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Warehouse scanning with Zebra RFD90",
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
                    Text(
                        "Welcome back",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Sign in with your ERP account",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
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
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
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
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                    )

                    error?.let {
                        Spacer(Modifier.height(14.dp))
                        StatusBanner(it, isError = true)
                    }

                    Spacer(Modifier.height(22.dp))

                    ErpPrimaryButton(
                        text = "Sign in",
                        loading = loading,
                        enabled = email.isNotBlank() && password.isNotBlank(),
                        onClick = {
                            loading = true
                            error = null
                            scope.launch {
                                val trimmedEmail = email.trim()
                                AppLog.i("Login attempt for $trimmedEmail → ${BuildConfig.API_BASE_URL}")
                                try {
                                    val response = container.api.login(
                                        LoginRequest(trimmedEmail, password),
                                    )
                                    AppLog.api("POST", "auth/login", response.code())
                                    val body = response.body()
                                    val token = body?.access_token
                                    if (!response.isSuccessful || token.isNullOrBlank()) {
                                        error = ApiErrorParser.httpMessage(response)
                                        AppLog.w("Login failed: HTTP ${response.code()} — $error")
                                        return@launch
                                    }
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
                                        return@launch
                                    }
                                    val wsPayload = com.erpcomplete.rfid.util.WorkflowJson
                                        .envelopeWorkspacesPayload(wsRes)
                                    AppLog.i(
                                        "Login success — ${wsPayload.businessUnits.size} BU(s), " +
                                            "${wsPayload.warehouses.size} warehouse(s)",
                                    )
                                    when {
                                        wsPayload.businessUnits.isEmpty() ->
                                            error = "No business unit assigned to this account."
                                        wsPayload.warehouses.isEmpty() ->
                                            error = "No warehouse assigned to this account."
                                        wsPayload.businessUnits.size == 1 &&
                                            wsPayload.warehouses.size == 1 &&
                                            wsPayload.warehouses.first().teamId != null -> {
                                            container.authStore.saveWorkspace(wsPayload.warehouses.first())
                                            container.refreshMobilePermissions()
                                            onLoginSuccess(false)
                                        }
                                        else -> onLoginSuccess(true)
                                    }
                                } catch (e: Exception) {
                                    error = ApiErrorParser.networkMessage(e)
                                    AppLog.e("Login network error", e)
                                } finally {
                                    loading = false
                                }
                            }
                        },
                    )
                }
            }

            if (BuildConfig.DEBUG) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "API: ${BuildConfig.API_BASE_URL}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
