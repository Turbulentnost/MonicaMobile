package com.example.monica.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.monica.R
import com.example.monica.ui.MonicaViewModel

private val LoginInk = Color(0xFF0A0A0A)
private val LoginFieldBg = Color(0xCC1A1A1A)
private val LoginFieldBorder = Color(0x66FFFFFF)
private val LoginFieldBorderFocused = Color(0xB3FFFFFF)
private val LoginText = Color(0xFFF5F5F5)
private val LoginMuted = Color(0xFFBDBDBD)
private val LoginButton = Color.Black
private val LoginError = Color(0xFFFF6B7A)

private val FieldShape = RoundedCornerShape(28.dp)
private val ButtonShape = RoundedCornerShape(999.dp)

@Composable
fun LoginScreen(
    vm: MonicaViewModel,
    onLoggedIn: () -> Unit,
    onRegister: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    fun submit() {
        if (loading || email.isBlank() || password.isBlank()) return
        vm.clearError()
        vm.login(email.trim(), password, onLoggedIn)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LoginInk),
    ) {
        Image(
            painter = painterResource(R.drawable.login_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 28.dp),
        ) {
            // Верх оставляем под логотип M на фоне
            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = FieldShape,
                    placeholder = {
                        Text("Логин / Email", color = LoginMuted)
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    colors = loginFieldColors(),
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = FieldShape,
                    placeholder = {
                        Text("Пароль", color = LoginMuted)
                    },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = if (passwordVisible) {
                                    "Скрыть пароль"
                                } else {
                                    "Показать пароль"
                                },
                                tint = LoginMuted,
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    colors = loginFieldColors(),
                )

                if (!error.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = error!!,
                        color = LoginError,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    onClick = { submit() },
                    enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = ButtonShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LoginButton,
                        contentColor = LoginText,
                        disabledContainerColor = LoginButton.copy(alpha = 0.55f),
                        disabledContentColor = LoginText.copy(alpha = 0.55f),
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        disabledElevation = 0.dp,
                    ),
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = LoginText,
                        )
                    } else {
                        Text(
                            "Войти",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { /* восстановление пароля пока не подключено */ },
                        enabled = !loading,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text("Забыли пароль?", color = LoginText, fontSize = 14.sp)
                    }
                    TextButton(
                        onClick = onRegister,
                        enabled = !loading,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text("Создать аккаунт", color = LoginText, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = LoginText,
    unfocusedTextColor = LoginText,
    cursorColor = LoginText,
    focusedContainerColor = LoginFieldBg,
    unfocusedContainerColor = LoginFieldBg,
    disabledContainerColor = LoginFieldBg,
    focusedBorderColor = LoginFieldBorderFocused,
    unfocusedBorderColor = LoginFieldBorder,
    disabledBorderColor = LoginFieldBorder,
    focusedPlaceholderColor = LoginMuted,
    unfocusedPlaceholderColor = LoginMuted,
)
