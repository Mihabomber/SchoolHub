# Шаблон: структура записи ИИ-модели (copy-paste)

Каталог: `app/src/main/java/com/school/hub/feature/ai/models/Models.kt`
Список:  `object ModelCatalog { val models = listOf( ... ) }`

---

## 1. Реальная запись — разобранная по структуре

```kotlin
LlmModel(
    "qwen3-17b",                                  // 1) id
    "Qwen 3 · 1.7B",                              // 2) name
    "Лучший выбор для 4 ГБ RAM, сильна в математике", // 3) tagline
    ChatFormat.CHATML,                            // 4) format
    "$HF/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf", // 5) url
    "Qwen3-1.7B-Q4_K_M.gguf",                     // 6) fileName
    1110,                                         // 7) sizeMb
    4.0,                                          // 8) minRamGb
    3,                                            // 9) russian
    contextSize = 4096,                           // 10) contextSize
    smart = 52,                                   // 11) smart
    errorRate = 28,                               // 12) errorRate
    year = 2025,                                  // 13) year
    systemSuffix = " /no_think",                  // 14) systemSuffix
    isNew = true,                                 // 15) isNew
)
```

`$HF` — это уже готовая константа `https://huggingface.co` (объявлена над каталогом),
пиши `"$HF/..."` как в примерах, а не полный адрес.

## 2. Порядок полей (первые 9 — только позиционные!)

| # | Поле | Тип | Что писать | Пример |
|---|------|-----|-----------|--------|
| 1 | `id` | String | уникальный, латиница/цифры/`-`, без пробелов — по нему модель находится в коде | `"mymodel-2b"` |
| 2 | `name` | String | видимое имя на карточке: «Бренд · размер» | `"Gemma 3 · 2B"` |
| 3 | `tagline` | String | одна строка описания под именем | `"Быстрая и хорошо знает русский"` |
| 4 | `format` | ChatFormat | `CHATML` \| `LLAMA3` \| `GEMMA` \| `PHI3` — см. таблицу ниже | `ChatFormat.CHATML` |
| 5 | `url` | String | прямая ссылка на GGUF: `"$HF/ORG/REPO/resolve/main/FILE.gguf"` | см. `MODELS-URL.txt` |
| 6 | `fileName` | String | **точно** последняя часть URL (после `/main/`) | `"model-q4_k_m.gguf"` |
| 7 | `sizeMb` | Int | размер файла в МБ (округлённый вверх) | `1710` |
| 8 | `minRamGb` | Double | минимум RAM телефона; обычно `sizeMb/1024 + 0.5..1.5` | `4.0` |
| 9 | `russian` | Int | 1..3 — качество русского языка (★☆☆ / ★★☆ / ★★★) | `3` |

Необязательные (по умолчанию): `contextSize = 2048`, `vision = false`, `smart = 50`,
`errorRate = 30`, `year = 2024`, `systemSuffix = ""`, `isNew = false`.

## 3. Какой `format` ставить

| Формат | Ставить моделям | Признак в названии |
|--------|-----------------|--------------------|
| `ChatFormat.CHATML` | Qwen 2.5/3, Gemma 2/3, LFM2, большинство современных | `Instruct`, `Chat`, `Qwen`, `Gemma`, `LFM` |
| `ChatFormat.LLAMA3` | Llama 2/3/3.2 | `Llama-3`, заголовки `<\|begin_of_text\|>` |
| `ChatFormat.GEMMA` | старые Gemma 1 | `gemma-1` |
| `ChatFormat.PHI3` | Phi-3/Phi-4-mini | `Phi-3`, `Phi-4` |

Не уверен — открой карточку похожей модели в `Models.kt` и скопируй её `format`.

## 4. Готовый пустой шаблон (вставь в `listOf(...)`)

```kotlin
LlmModel(
    "CHANGE-ID",
    "CHANGE-NAME · CHANGE-SIZE",
    "CHANGE-TAGLINE",
    ChatFormat.CHATML,
    "$HF/CHANGE-ORG/CHANGE-REPO/resolve/main/CHANGE-FILE.gguf",
    "CHANGE-FILE.gguf",
    0,      // sizeMb: ls -la файл | размер/1048576, округли вверх
    0.0,    // minRamGb: sizeMb/1024 + ~1
    2,      // russian: 1..3
    contextSize = 2048,
    smart = 50,      // 0..100, своя оценка
    errorRate = 30,  // 0..100, % ошибок
    year = 2025,
),
```

## 5. Чек-лист перед коммитом

- [ ] `fileName` == последняя часть `url` (иначе файл не ляжет куда надо)
- [ ] `sizeMb` совпадает с реальным размером (от него зависит место и прогресс)
- [ ] ссылка живая: `curl -sIL "<url>" | head -5` → 200/302
- [ ] `id` уникален в списке
- [ ] формат чата выбран по таблице выше
- [ ] `minRamGb` не меньше размера модели в ГБ + запас
- [ ] собрал (GitHub Actions → релиз) и проверил: ИИ офлайн → карточка → Скачать → Чат

## 6. Типичные ошибки

| Симптом | Причина |
|---------|---------|
| Кнопка «Скачать» сразу даёт ошибку | не хватает места: нужно `sizeMb + 200` МБ свободно |
| Прогресс 0 и сразу «Ошибка» | битая ссылка или `url`/`fileName` не совпадают |
| Чат пишет кашу вместо ответов | неправильный `format` (попробуй другой) |
| Модель видна, но не скачивается по Wi-Fi | включён тумблер «Скачивать только по Wi-Fi» |
| Мало RAM на телефоне | подними `minRamGb` — карточка честно скажет «не хватит» |
