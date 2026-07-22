package com.example.monica.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.monica.ui.MonicaViewModel

private const val TOTAL_STEPS = 4
private val NICKNAME_REGEX = Regex("^[a-zA-Z0-9_]{3,50}$")

@Composable
fun RegistrationScreen(
    vm: MonicaViewModel,
    onRegistered: () -> Unit,
    onBackToLogin: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var debugCode by rememberSaveable { mutableStateOf<String?>(null) }
    var registrationToken by rememberSaveable { mutableStateOf("") }
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var birthDate by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }

    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    val pickPhoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        photoUri = uri?.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Регистрация", style = MaterialTheme.typography.headlineLarge)
        Text(
            when (step) {
                0 -> "Введите email"
                1 -> "Подтверждение email"
                2 -> "Ваш профиль"
                else -> "Фото профиля"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Шаг ${step + 1} из $TOTAL_STEPS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (step + 1f) / TOTAL_STEPS },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))

        when (step) {
            0 -> EmailStep(
                email = email,
                onEmailChange = { email = it },
                loading = loading,
                onNext = {
                    vm.clearError()
                    vm.sendRegistrationCode(email.trim()) { debug ->
                        debugCode = debug
                        step = 1
                    }
                },
            )

            1 -> CodeStep(
                email = email,
                code = code,
                debugCode = debugCode,
                onCodeChange = { code = it.filter(Char::isDigit).take(6) },
                loading = loading,
                onNext = {
                    vm.clearError()
                    vm.verifyRegistrationCode(email.trim(), code) { token ->
                        registrationToken = token
                        step = 2
                    }
                },
            )

            2 -> ProfileStep(
                firstName = firstName,
                lastName = lastName,
                nickname = nickname,
                city = city,
                birthDate = birthDate,
                password = password,
                onFirstNameChange = { firstName = it },
                onLastNameChange = { lastName = it },
                onNicknameChange = { nickname = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' }.take(50) },
                onCityChange = { city = it },
                onBirthDateChange = { birthDate = it.filter { ch -> ch.isDigit() || ch == '-' }.take(10) },
                onPasswordChange = { password = it },
                loading = loading,
                onNext = {
                    vm.clearError()
                    vm.saveRegistrationProfile(
                        registrationToken = registrationToken,
                        firstName = firstName.trim(),
                        lastName = lastName.trim(),
                        password = password,
                        nickname = nickname.trim(),
                        city = city.trim(),
                        birthDate = birthDate.trim().ifBlank { null },
                        onSuccess = { step = 3 },
                    )
                },
            )

            else -> AvatarStep(
                photoUri = photoUri?.let(Uri::parse),
                loading = loading,
                onPickPhoto = {
                    pickPhoto.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onSkip = {
                    vm.clearError()
                    vm.completeRegistration(
                        registrationToken = registrationToken,
                        avatarUri = null,
                        onSuccess = onRegistered,
                    )
                },
                onFinish = {
                    vm.clearError()
                    vm.completeRegistration(
                        registrationToken = registrationToken,
                        avatarUri = photoUri?.let(Uri::parse),
                        onSuccess = onRegistered,
                    )
                },
            )
        }

        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }

        if (step < 3) {
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    vm.clearError()
                    onBackToLogin()
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Уже есть аккаунт? Войти")
            }
        }
    }
}

@Composable
private fun EmailStep(
    email: String,
    onEmailChange: (String) -> Unit,
    loading: Boolean,
    onNext: () -> Unit,
) {
    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = { Text("Email") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onNext,
        enabled = !loading && email.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (loading) CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
        else Text("Далее")
    }
}

@Composable
private fun CodeStep(
    email: String,
    code: String,
    debugCode: String?,
    onCodeChange: (String) -> Unit,
    loading: Boolean,
    onNext: () -> Unit,
) {
    Text(
        "Код отправлен на $email",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (!debugCode.isNullOrBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Debug-код: $debugCode",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = code,
        onValueChange = onCodeChange,
        label = { Text("Код из письма") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onNext,
        enabled = !loading && code.length == 6,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (loading) CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
        else Text("Подтвердить")
    }
}

@Composable
private fun ProfileStep(
    firstName: String,
    lastName: String,
    nickname: String,
    city: String,
    birthDate: String,
    password: String,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onNicknameChange: (String) -> Unit,
    onCityChange: (String) -> Unit,
    onBirthDateChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    loading: Boolean,
    onNext: () -> Unit,
) {
    Text(
        "У вас 5 минут на заполнение данных",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    RegistrationField(firstName, onFirstNameChange, "Имя *")
    RegistrationField(lastName, onLastNameChange, "Фамилия *")
    RegistrationField(nickname, onNicknameChange, "Никнейм *")
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text("Пароль — минимум 8 символов") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    RegistrationField(city, onCityChange, "Город")
    OutlinedTextField(
        value = birthDate,
        onValueChange = onBirthDateChange,
        label = { Text("Дата рождения (ГГГГ-ММ-ДД)") },
        placeholder = { Text("1990-01-15") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onNext,
        enabled = !loading &&
            firstName.isNotBlank() &&
            lastName.isNotBlank() &&
            NICKNAME_REGEX.matches(nickname.trim()) &&
            password.length >= 8,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (loading) CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
        else Text("Далее")
    }
}

@Composable
private fun AvatarStep(
    photoUri: Uri?,
    loading: Boolean,
    onPickPhoto: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Загрузите аватар или пропустите этот шаг",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable(enabled = !loading, onClick = onPickPhoto),
            contentAlignment = Alignment.Center,
        ) {
            if (photoUri != null) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = "Предпросмотр аватара",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    "Нет фото",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick = onPickPhoto,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (photoUri == null) "Выбрать фото" else "Изменить фото")
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onSkip,
                enabled = !loading,
                modifier = Modifier.weight(1f),
            ) {
                if (loading && photoUri == null) {
                    CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("Пропустить")
                }
            }
            Button(
                onClick = onFinish,
                enabled = !loading && photoUri != null,
                modifier = Modifier.weight(1f),
            ) {
                if (loading && photoUri != null) {
                    CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("Завершить")
                }
            }
        }
    }
}

@Composable
private fun RegistrationField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
}
