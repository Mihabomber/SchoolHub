package com.school.hub.feature.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/**
 * Пользовательское соглашение и согласие на обработку персональных данных (152-ФЗ).
 * Согласие на обработку ПД оформлено отдельно от соглашения (ст. 9 ч. 1 152-ФЗ).
 * При изменении текстов увеличь VERSION: все пользователи один раз подтвердят новые условия.
 */
object Consent {
    const val VERSION = 1
    const val DATE = "26.09.2026"

    /** ЗАПОЛНИТЬ: ФИО (или название) владельца приложения. */
    const val OPERATOR = "Владелец приложения «Парта»"
    val CONTACT: String get() = Owners.emails.first()

    const val REQUIRED = "Чтобы продолжить, прими соглашение и дай согласие на обработку данных"

    val terms: String get() = """
Пользовательское соглашение приложения «Парта»
Редакция $VERSION от $DATE

1. Кто мы. Приложение «Парта» (далее — приложение) предоставляет $OPERATOR (далее — владелец). Связь: $CONTACT.

2. Что это. Приложение — школьный помощник: калькулятор, переводчик, ИИ-чат, игры, браузер и общение с классом. Приложение бесплатное и предоставляется «как есть».

3. Аккаунт. Для входа нужен аккаунт (почта и пароль, Google или Apple). Пароль знает только сервис Firebase, владелец и администраторы его не видят. Ты отвечаешь за сохранность своего пароля.

4. Имя в профиле. Имя и фамилию из профиля видят одноклассники и администратор класса. Ты можешь указать имя по своему выбору. Проверка на «настоящесть» не проводится, и за это аккаунт не блокируют.

5. Правила. Нельзя: оскорблять и травить других, рассылать спам, публиковать незаконные материалы, пытаться взломать приложение или чужие аккаунты. За нарушение этих правил администратор может ограничить доступ к аккаунту. Если это ошибка — напиши на $CONTACT.

6. ИИ и переводы. Ответы ИИ и машинный перевод могут содержать ошибки. Проверяй важную информацию.

7. Данные. Как мы обрабатываем персональные данные, описано в отдельном документе «Согласие на обработку персональных данных и политика». Согласие даётся отдельно.

8. Изменения. Если соглашение изменится, приложение покажет новую редакцию и попросит принять её снова.

9. Удаление аккаунта. Напиши на $CONTACT с почты аккаунта, и мы удалим аккаунт и связанные с ним данные.
""".trim()

    val policy: String get() = """
Согласие на обработку персональных данных и политика обработки
Редакция $VERSION от $DATE

Отмечая этот пункт, я свободно, своей волей и в своём интересе даю согласие на обработку моих персональных данных на условиях ниже (ст. 9 Федерального закона от 27.07.2006 № 152-ФЗ «О персональных данных»).

1. Оператор: $OPERATOR. Контакт для обращений: $CONTACT.

2. Какие данные обрабатываются:
- адрес электронной почты;
- имя и фамилия, которые я указал(а) в профиле;
- способ входа (почта, Google или Apple) и идентификатор аккаунта;
- класс, если я указал(а) его в настройках;
- модель устройства, версия приложения, идентификатор установки приложения;
- дата регистрации и время последнего входа;
- статистика использования функций приложения (счётчики);
- отметка о моём согласии (версия и время).
Пароль оператор не получает: его хранит сервис Firebase в зашифрованном (хешированном) виде.

3. Цели обработки:
- создание аккаунта, вход и восстановление доступа;
- показ моего имени одноклассникам и администратору класса;
- работа функций общения с классом;
- защита от злоупотреблений и соблюдение правил;
- аналитика использования и улучшение приложения.

4. Действия с данными: сбор, запись, систематизация, накопление, хранение, уточнение (обновление, изменение), извлечение, использование, передача (предоставление, доступ), обезличивание, блокирование, удаление, уничтожение. Обработка автоматизированная.

5. Кому передаются данные:
- администраторам класса в приложении (имя, почта, статистика);
- Google LLC (сервисы Firebase Authentication и Cloud Firestore) — хранение аккаунта и данных;
- Apple Inc. — только если я вхожу через Apple.
Статистика может пересылаться на сервер через устройства одноклассников, если у меня нет интернета.

6. Трансграничная передача. Я согласен(на), что данные передаются и хранятся на серверах Google за пределами Российской Федерации (в том числе в США и странах ЕС), в соответствии со ст. 12 152-ФЗ.

7. Срок. Согласие действует, пока существует мой аккаунт, или до его отзыва. После отзыва или удаления аккаунта данные удаляются в течение 30 дней, если закон не требует хранить их дольше.

8. Отзыв согласия. Я могу отозвать согласие в любой момент: написать на $CONTACT с почты аккаунта. Без согласия пользоваться аккаунтом нельзя, поэтому после отзыва аккаунт удаляется.

9. Мои права (ст. 14 152-ФЗ): получить сведения об обработке моих данных, потребовать их уточнения, блокирования или удаления, обжаловать действия оператора в Роскомнадзоре или в суде.

10. Защита. Данные передаются по защищённому соединению. Доступ к профилям есть только у владельца аккаунта и администраторов.

11. Возраст. Если мне меньше 14 лет, это согласие дал(а) мой родитель или законный представитель. Отмечая пункт, я подтверждаю, что мне есть 14 лет или что родитель (законный представитель) согласен.

12. Проверка данных. Оператор не проверяет, настоящие ли имя и фамилия, и не блокирует аккаунт за это.
""".trim()
}

/** Два отдельных обязательных пункта: соглашение и согласие на обработку ПД. */
@Composable
fun ConsentChecks(
    terms: Boolean,
    onTerms: (Boolean) -> Unit,
    pd: Boolean,
    onPd: (Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(0) }
    ConsentRow(terms, onTerms, "Принимаю Пользовательское соглашение", "Открыть соглашение") { open = 1 }
    ConsentRow(
        pd, onPd,
        "Даю согласие на обработку моих персональных данных, в том числе на их передачу на серверы Google Firebase за пределами РФ. Мне есть 14 лет или согласие дал родитель (законный представитель).",
        "Открыть согласие и политику",
    ) { open = 2 }
    when (open) {
        1 -> ConsentDialog("Пользовательское соглашение", Consent.terms) { open = 0 }
        2 -> ConsentDialog("Согласие на обработку персональных данных", Consent.policy) { open = 0 }
    }
}

@Composable
private fun ConsentRow(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    text: String,
    link: String,
    onOpen: () -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        Checkbox(checked, onChange)
        Column(Modifier.weight(1f).padding(top = 12.dp)) {
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { onChange(!checked) })
            Text(
                link,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
                modifier = Modifier.clickable(onClick = onOpen).padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
fun ConsentDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Понятно") } },
        title = { Text(title) },
        text = {
            Box(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}
