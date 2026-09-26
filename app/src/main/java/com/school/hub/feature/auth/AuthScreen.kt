package com.school.hub.feature.auth

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.hub.core.ui.theme.GlassCard
import com.school.hub.navigation.AppViewModelFactory
import kotlinx.coroutines.launch

class AuthViewModel(val repo: AuthRepository) : ViewModel() {
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var info by mutableStateOf<String?>(null); private set
    private fun run(ok: String? = null, block: suspend () -> String?) { if (busy) return; busy = true; error = null; info = null; viewModelScope.launch { val e = block(); error = e; if (e == null) info = ok; busy = false } }
    fun login(e: String, p: String) = run { repo.login(e, p) }
    fun register(f: String, l: String, e: String, p: String, consent: Boolean) = run("Письмо для подтверждения отправлено на $e") { repo.register(f, l, e, p, consent) }
    fun reset(e: String) = run("Ссылка для сброса пароля отправлена на $e") { repo.resetPassword(e) }
    fun resend() = run("Письмо отправлено ещё раз (проверь «Спам»)") { repo.resendVerification() }
    fun check() = run { repo.checkVerified() }
    fun google(a: Activity) = run { repo.google(a) }
    fun apple(a: Activity) = run { repo.apple(a) }
    fun saveProfile(f: String, l: String, consent: Boolean) = run { repo.saveProfile(f, l, consent) }
    fun logout() = run { repo.logout(); null }
}

private val Warn = Color(0xFF8E8C99)
@Composable private fun Warning(text: String) = Text(text, color = Warn, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))

@Composable fun AuthGate(state: AuthState, vm: AuthViewModel = viewModel(factory = AppViewModelFactory.Factory)) {
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFFF3B2D), Color(0xFFFFB3C0), Color(0xFFFFF4D8)))), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().widthIn(max = 480.dp).verticalScroll(rememberScrollState()).systemBarsPadding().imePadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("\\uD83C\\uDF92", fontSize = 56.sp); Text("Парта", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold); Text("Школьный помощник всего класса", color = Color.White.copy(alpha = .8f)); Spacer(Modifier.height(20.dp))
            GlassCard(Modifier.fillMaxWidth(), forceGlass = true) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { AnimatedContent(state, label = "auth") { s -> when (s) { AuthState.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }; AuthState.NotConfigured -> Text("Firebase не настроен: добавь google-services.json в app/.", textAlign = TextAlign.Center); AuthState.SignedOut -> SignInForm(vm); is AuthState.VerifyEmail -> VerifyForm(s.email, vm); is AuthState.NeedProfile -> ProfileForm(s, vm); is AuthState.Blocked -> BlockedForm(s, vm); is AuthState.Ready -> Unit } }; vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }; vm.info?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) } } }
        }
    }
}

@Composable private fun PassField(value: String, onChange: (String) -> Unit, label: String) { var show by remember { mutableStateOf(false) }; OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), trailingIcon = { IconButton(onClick = { show = !show }) { Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null) } }) }

@Composable private fun SignInForm(vm: AuthViewModel) { val activity = LocalContext.current as Activity; var register by rememberSaveable { mutableStateOf(false) }; var first by rememberSaveable { mutableStateOf("") }; var last by rememberSaveable { mutableStateOf("") }; var email by rememberSaveable { mutableStateOf("") }; var pass by remember { mutableStateOf("") }; var pass2 by remember { mutableStateOf("") }; var terms by rememberSaveable { mutableStateOf(false) }; var pd by rememberSaveable { mutableStateOf(false) }; val agreed = terms && pd; TabRow(if (register) 1 else 0, containerColor = Color.Transparent) { Tab(!register, { register = false }, text = { Text("Вход") }); Tab(register, { register = true }, text = { Text("Регистрация") }) }; if (register) { OutlinedTextField(first, { first = it }, label = { Text("Имя") }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(last, { last = it }, label = { Text("Фамилия") }, singleLine = true, modifier = Modifier.fillMaxWidth()); Warning("Имя и фамилию видят одноклассники и администратор класса.") }; OutlinedTextField(email, { email = it.trim() }, label = { Text("Электронная почта") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)); if (register) Warning("На эту почту придёт письмо с подтверждением."); PassField(pass, { pass = it }, "Пароль"); if (register) { PassField(pass2, { pass2 = it }, "Повтори пароль"); Warning("Минимум 8 символов, буквы и цифры. Пароль не видит никто, даже админ."); ConsentChecks(terms, { terms = it }, pd, { pd = it }); Button(onClick = { vm.register(first, last, email, pass, agreed) }, enabled = !vm.busy && agreed && pass == pass2 && pass.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(if (pass2.isNotEmpty() && pass != pass2) "Пароли не совпадают" else "Создать аккаунт") } } else { Button(onClick = { vm.login(email, pass) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Войти") }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { vm.reset(email) }) { Text("Забыли пароль?") } } }; if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth()); Row(verticalAlignment = Alignment.CenterVertically) { HorizontalDivider(Modifier.weight(1f)); Text("  или  ", style = MaterialTheme.typography.labelSmall); HorizontalDivider(Modifier.weight(1f)) }; if (!register) ConsentChecks(terms, { terms = it }, pd, { pd = it }); OutlinedButton(onClick = { vm.google(activity) }, enabled = !vm.busy && agreed, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("G", fontWeight = FontWeight.ExtraBold, color = Color(0xFF4285F4)); Spacer(Modifier.width(10.dp)); Text("Войти через Google") }; Button(onClick = { vm.apple(activity) }, enabled = !vm.busy && agreed, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)) { Text("Войти через Apple") }; if (!agreed) Warning("Для входа через Google или Apple нужно принять оба документа."); Warning("Согласие на обработку имени и почты нужно для работы аккаунта.") }

@Composable private fun VerifyForm(email: String, vm: AuthViewModel) { Text("\\uD83D\\uDCE7 Проверь почту", style = MaterialTheme.typography.titleLarge); Text("Открой письмо и подтверди адрес $email.", textAlign = TextAlign.Start); Button(onClick = vm::check, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Я подтвердил(а) почту") }; OutlinedButton(onClick = vm::resend, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Отправить письмо ещё раз") }; TextButton(onClick = vm::logout) { Text("Выйти") } }

@Composable private fun ProfileForm(s: AuthState.NeedProfile, vm: AuthViewModel) { var first by rememberSaveable(s.email) { mutableStateOf(s.first) }; var last by rememberSaveable(s.email) { mutableStateOf(s.last) }; var terms by rememberSaveable(s.email) { mutableStateOf(false) }; var pd by rememberSaveable(s.email) { mutableStateOf(false) }; if (s.consentOnly) Text("Подтверди согласия", style = MaterialTheme.typography.titleLarge) else Text("Как тебя зовут?", style = MaterialTheme.typography.titleLarge); Text(s.email, style = MaterialTheme.typography.bodySmall); OutlinedTextField(first, { first = it }, label = { Text("Имя") }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(last, { last = it }, label = { Text("Фамилия") }, singleLine = true, modifier = Modifier.fillMaxWidth()); Warning("Имя и фамилию видят одноклассники и администратор класса."); ConsentChecks(terms, { terms = it }, pd, { pd = it }); Button(onClick = { vm.saveProfile(first, last, terms && pd) }, enabled = !vm.busy && terms && pd, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Продолжить") }; TextButton(onClick = vm::logout) { Text("Выйти") } }

@Composable private fun BlockedForm(s: AuthState.Blocked, vm: AuthViewModel) { Text("\\u26D4 Доступ ограничен", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error); Text(s.email); Text("Причина: ${s.reason}"); Warning("Если это ошибка, напиши администратору класса."); OutlinedButton(onClick = vm::logout, modifier = Modifier.fillMaxWidth()) { Text("Выйти") } }
