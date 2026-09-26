package com.school.hub.feature.auth

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Patterns
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.school.hub.BuildConfig
import com.school.hub.core.data.SettingsStore
import com.school.hub.feature.social.StatsTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/** Владельцы приложения — всегда админы (дублируется в firestore.rules). */
object Owners {
    val emails = setOf("mihail2014565@gmail.com", "netrussia1488@gmail.com")
}

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val role: String = "user",
    val blocked: Boolean = false,
    val blockReason: String = "",
    val provider: String = "",
    val createdAt: Long = 0,
    val lastSeen: Long = 0,
    val appVersion: String = "",
    val device: String = "",
    val consentVersion: Int = 0,
) {
    val fullName: String get() = "$firstName $lastName".trim()
    val isAdmin: Boolean get() = role == "admin" || email.lowercase() in Owners.emails
}

fun DocumentSnapshot.toProfile() = UserProfile(
    uid = id,
    email = getString("email").orEmpty(),
    firstName = getString("firstName").orEmpty(),
    lastName = getString("lastName").orEmpty(),
    role = getString("role") ?: "user",
    blocked = getBoolean("blocked") ?: false,
    blockReason = getString("blockReason").orEmpty(),
    provider = getString("provider").orEmpty(),
    createdAt = getLong("createdAt") ?: 0,
    lastSeen = getLong("lastSeen") ?: 0,
    appVersion = getString("appVersion").orEmpty(),
    device = getString("device").orEmpty(),
    consentVersion = (getLong("consentVersion") ?: 0L).toInt(),
)

sealed interface AuthState {
    data object Loading : AuthState
    data object NotConfigured : AuthState
    data object SignedOut : AuthState
    data class VerifyEmail(val email: String) : AuthState
    /** consentOnly = имя уже есть, нужно только принять (новые) условия. */
    data class NeedProfile(val email: String, val first: String, val last: String, val consentOnly: Boolean = false) : AuthState
    data class Blocked(val email: String, val reason: String) : AuthState
    data class Ready(val profile: UserProfile) : AuthState
}

/**
 * Проверка формата. Настоящие ли имя и фамилия, не проверяется,
 * и за это аккаунт не блокируется.
 */
object Validators {
    private val nameRe = Regex("^\\p{L}[\\p{L}' -]*$")
    private val tempMail = listOf(
        "mailinator", "tempmail", "temp-mail", "10minutemail", "guerrillamail", "yopmail",
        "trashmail", "sharklasers", "dropmail", "getnada", "maildrop",
    )

    fun normalizeName(s: String): String =
        s.trim().replace(Regex("\\s+"), " ").split(" ").joinToString(" ") { w ->
            w.split("-").joinToString("-") { p -> p.lowercase().replaceFirstChar { it.uppercase() } }
        }

    fun name(first: String, last: String): String? {
        val f = normalizeName(first); val l = normalizeName(last)
        return when {
            f.isEmpty() || l.isEmpty() -> "Введи имя и фамилию"
            f.length > 40 || l.length > 40 -> "Имя или фамилия слишком длинные (до 40 символов)"
            !nameRe.matches(f) || !nameRe.matches(l) -> "В имени и фамилии можно использовать буквы, пробел, дефис и апостроф"
            else -> null
        }
    }

    fun email(e: String): String? {
        val v = e.trim().lowercase()
        return when {
            !Patterns.EMAIL_ADDRESS.matcher(v).matches() -> "Неверный адрес почты"
            tempMail.any { v.substringAfter('@').contains(it) } -> "Временные почты не подходят: письмо с подтверждением может не дойти"
            else -> null
        }
    }

    fun password(p: String): String? = when {
        p.length < 8 -> "Пароль — минимум 8 символов"
        p.none { it.isLetter() } || p.none { it.isDigit() } -> "В пароле нужны и буквы, и цифры"
        p.lowercase() in setOf("qwerty123", "password1", "12345678a", "parol123") -> "Слишком простой пароль"
        else -> null
    }
}

/**
 * Аккаунты на Firebase: Authentication (почта+пароль, Google, Apple) + Firestore (профиль, роль, блокировка).
 * Пароли хранит только Firebase в виде хеша — их не видит никто, даже админ.
 * Сессия переживает обновления приложения (тот же пакет и подпись).
 * Согласие на обработку ПД (152-ФЗ) хранится в профиле: consentVersion и consentAt.
 */
class AuthRepository(
    private val context: Context,
    private val settings: SettingsStore,
    private val stats: StatsTracker,
) {
    val available: Boolean = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()
    val profile: UserProfile? get() = (_state.value as? AuthState.Ready)?.profile
    val isSignedIn: Boolean get() = available && auth.currentUser != null

    private var userReg: ListenerRegistration? = null
    private var banReg: ListenerRegistration? = null
    private var listenedUid: String? = null
    private var doc: UserProfile? = null
    private var docLoaded = false
    private var banReason: String? = null
    private var touched = false

    fun start() {
        if (!available) { _state.value = AuthState.NotConfigured; return }
        auth.addAuthStateListener { onUser(it.currentUser) }
    }

    private fun onUser(u: FirebaseUser?) {
        if (u == null) {
            detach(); _state.value = AuthState.SignedOut; return
        }
        val email = u.email.orEmpty().lowercase()
        val byPassword = u.providerData.any { it.providerId == "password" } &&
            u.providerData.none { it.providerId == "google.com" || it.providerId == "apple.com" }
        if (byPassword && !u.isEmailVerified) { detach(); _state.value = AuthState.VerifyEmail(email); return }
        if (listenedUid == u.uid) { evaluate(); return }
        detach()
        listenedUid = u.uid
        // Офлайн-старт: сразу показываем последний известный профиль.
        cachedProfile(u.uid)?.let { doc = it; docLoaded = true; banReason = prefs.getString("ban_${u.uid}", null) }
        evaluate()
        userReg = db.collection("users").document(u.uid).addSnapshotListener { snap, err ->
            if (err != null || snap == null) { if (!docLoaded) { docLoaded = true }; evaluate(); return@addSnapshotListener }
            if (!snap.exists() && snap.metadata.isFromCache && doc != null) return@addSnapshotListener
            doc = if (snap.exists()) snap.toProfile() else null
            docLoaded = true
            doc?.let(::cache)
            evaluate()
        }
        if (email.isNotBlank()) banReg = db.collection("banned").document(email).addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            if (!snap.exists() && snap.metadata.isFromCache) return@addSnapshotListener
            banReason = if (snap.exists()) (snap.getString("reason") ?: "Нарушение правил") else null
            prefs.edit().apply { if (banReason != null) putString("ban_${u.uid}", banReason) else remove("ban_${u.uid}") }.apply()
            evaluate()
        }
    }

    private fun evaluate() {
        val u = auth.currentUser ?: return
        val email = u.email.orEmpty().lowercase()
        val d = doc
        _state.value = when {
            banReason != null -> AuthState.Blocked(email, banReason!!)
            d?.blocked == true -> AuthState.Blocked(email, d.blockReason.ifBlank { "Нарушение правил" })
            !docLoaded -> AuthState.Loading
            d == null || d.firstName.isBlank() || d.lastName.isBlank() -> {
                val parts = u.displayName.orEmpty().split(" ")
                AuthState.NeedProfile(email, prefs.getString("sug_first", null) ?: parts.getOrElse(0) { "" },
                    prefs.getString("sug_last", null) ?: parts.drop(1).joinToString(" "))
            }
            d.consentVersion < Consent.VERSION -> AuthState.NeedProfile(email, d.firstName, d.lastName, consentOnly = true)
            else -> {
                if (settings.userName.value != d.fullName) settings.setUserName(d.fullName)
                stats.email = d.email
                if (!touched) { touched = true; touch(u.uid) }
                AuthState.Ready(d)
            }
        }
    }

    private fun touch(uid: String) {
        db.collection("users").document(uid).set(
            mapOf("lastSeen" to System.currentTimeMillis(), "appVersion" to BuildConfig.VERSION_NAME,
                "device" to "${Build.MANUFACTURER} ${Build.MODEL}", "deviceId" to settings.deviceId),
            SetOptions.merge(),
        )
    }

    private fun detach() {
        userReg?.remove(); banReg?.remove(); userReg = null; banReg = null
        listenedUid = null; doc = null; docLoaded = false; banReason = null; touched = false
    }

    private fun cache(p: UserProfile) {
        prefs.edit().putString("p_${p.uid}", listOf(p.email, p.firstName, p.lastName, p.role, p.blocked.toString(), p.blockReason,
            p.consentVersion.toString()).joinToString("\u0001")).apply()
    }

    private fun cachedProfile(uid: String): UserProfile? {
        val v = prefs.getString("p_$uid", null)?.split("\u0001") ?: return null
        if (v.size < 6) return null
        return UserProfile(uid, v[0], v[1], v[2], v[3], v[4].toBoolean(), v[5],
            consentVersion = v.getOrNull(6)?.toIntOrNull() ?: 0)
    }

    // ---------- Действия (возвращают текст ошибки или null) ----------

    suspend fun register(first: String, last: String, email: String, pass: String, consent: Boolean = false): String? {
        if (!consent) return Consent.REQUIRED
        Validators.name(first, last)?.let { return it }
        Validators.email(email)?.let { return it }
        Validators.password(pass)?.let { return it }
        return guard {
            val res = auth.createUserWithEmailAndPassword(email.trim().lowercase(), pass).await()
            val u = res.user ?: error("Не удалось создать аккаунт")
            writeProfile(u, Validators.normalizeName(first), Validators.normalizeName(last), "password", create = true)
            u.sendEmailVerification().await()
        }
    }

    suspend fun login(email: String, pass: String): String? {
        if (email.isBlank() || pass.isBlank()) return "Введи почту и пароль"
        return guard { auth.signInWithEmailAndPassword(email.trim().lowercase(), pass).await() }
    }

    suspend fun resetPassword(email: String): String? {
        Validators.email(email)?.let { return it }
        return guard { auth.sendPasswordResetEmail(email.trim().lowercase()).await() }
    }

    suspend fun resendVerification(): String? = guard { auth.currentUser?.sendEmailVerification()?.await() }

    suspend fun checkVerified(): String? = guard {
        auth.currentUser?.reload()?.await()
        auth.currentUser?.let { if (it.isEmailVerified) it.getIdToken(true).await() }
        onUser(auth.currentUser)
        if (_state.value is AuthState.VerifyEmail) error("Почта ещё не подтверждена — открой письмо и нажми на ссылку")
    }

    suspend fun google(activity: Activity): String? {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId == 0) return "Вход через Google ещё не настроен в Firebase (нужен SHA-1 и новый google-services.json — см. FIREBASE-SETUP.md)"
        return guard {
            val option = GetSignInWithGoogleOption.Builder(context.getString(resId)).build()
            val req = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val cred = CredentialManager.create(activity).getCredential(activity, req).credential
            if (cred !is CustomCredential || cred.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) error("Google вернул неизвестный ответ")
            val g = GoogleIdTokenCredential.createFrom(cred.data)
            prefs.edit().putString("sug_first", g.givenName.orEmpty()).putString("sug_last", g.familyName.orEmpty()).apply()
            auth.signInWithCredential(GoogleAuthProvider.getCredential(g.idToken, null)).await()
        }
    }

    suspend fun apple(activity: Activity): String? = guard {
        val provider = OAuthProvider.newBuilder("apple.com").setScopes(listOf("email", "name")).build()
        val res = auth.pendingAuthResult?.await() ?: auth.startActivityForSignInWithProvider(activity, provider).await()
        res.user?.displayName?.split(" ")?.let {
            prefs.edit().putString("sug_first", it.getOrElse(0) { "" }).putString("sug_last", it.drop(1).joinToString(" ")).apply()
        }
    }

    suspend fun saveProfile(first: String, last: String, consent: Boolean = false): String? {
        if (!consent) return Consent.REQUIRED
        Validators.name(first, last)?.let { return it }
        val u = auth.currentUser ?: return "Сначала войди"
        val provider = u.providerData.map { it.providerId }.firstOrNull { it != "firebase" } ?: "password"
        return guard {
            writeProfile(u, Validators.normalizeName(first), Validators.normalizeName(last), provider, create = doc == null)
            prefs.edit().remove("sug_first").remove("sug_last").apply()
        }
    }

    /** Пишет профиль вместе с отметкой о согласии (вызывается только после согласия пользователя). */
    private suspend fun writeProfile(u: FirebaseUser, first: String, last: String, provider: String, create: Boolean) {
        val now = System.currentTimeMillis()
        val ref = db.collection("users").document(u.uid)
        val base = mapOf("firstName" to first, "lastName" to last, "provider" to provider, "lastSeen" to now,
            "appVersion" to BuildConfig.VERSION_NAME, "device" to "${Build.MANUFACTURER} ${Build.MODEL}", "deviceId" to settings.deviceId,
            "consentVersion" to Consent.VERSION, "consentAt" to now)
        if (create) ref.set(base + mapOf("email" to u.email.orEmpty().lowercase(), "role" to "user", "blocked" to false,
            "blockReason" to "", "createdAt" to now)).await()
        else ref.set(base, SetOptions.merge()).await()
    }

    suspend fun logout() {
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        if (available) auth.signOut()
    }

    private suspend fun guard(block: suspend () -> Unit): String? = try {
        block(); null
    } catch (e: GetCredentialCancellationException) { null
    } catch (e: NoCredentialException) { "На телефоне нет Google-аккаунта — добавь его в настройках Android"
    } catch (e: FirebaseAuthUserCollisionException) { "Эта почта уже зарегистрирована — просто войди"
    } catch (e: FirebaseAuthWeakPasswordException) { "Слишком простой пароль"
    } catch (e: FirebaseAuthInvalidUserException) { "Аккаунт не найден или отключён"
    } catch (e: FirebaseAuthInvalidCredentialsException) { "Неверная почта или пароль"
    } catch (e: FirebaseNetworkException) { "Нет интернета. Для входа и регистрации нужен интернет (дальше приложение работает офлайн)"
    } catch (e: FirebaseTooManyRequestsException) { "Слишком много попыток. Подожди пару минут"
    } catch (e: Exception) { e.message ?: "Ошибка: ${e.javaClass.simpleName}" }
}
