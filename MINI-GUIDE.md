# Мини-гайд: как прикрепить другую ИИ-модель в SchoolHub

Вся правка — **один файл**:
`app/src/main/java/com/school/hub/feature/ai/models/Models.kt` → объект **`ModelCatalog`** → список `models`.

---

## 1. Найди модель

Нужен файл формата **GGUF**, лучше квантование **Q4_K_M** (компромисс размер/качество).
Лежит обычно на Hugging Face: `https://huggingface.co/<репозиторий>/resolve/main/<файл>.gguf`
Все готовые ссылки, которые уже используются в приложении — в файле **`MODELS-URL.txt`** рядом.

Проверь ссылку (должна отдать файл, код 200/302):

```bash
curl -sIL "https://huggingface.co/ORG/REPO/resolve/main/FILE.gguf" | head -5
```

> Если Hugging Face заблокирован/недоступен — рабочее зеркало:
> `https://hf-mirror.com/...` (просто замени `huggingface.co` на `hf-mirror.com`).
> Приложение само пробует зеркало при сбое, руками менять ничего не нужно.

## 2. Добавь запись в каталог

В `Models.kt`, внутри `object ModelCatalog { val models = listOf( ... ) }` добавь:

```kotlin
LlmModel(
    "my-model",                       // id — уникальный, латиница, без пробелов
    "Название · 1B",                  // name — как показывать в списке
    "Короткое описание на карточке",  // tagline
    ChatFormat.CHATML,                // формат чата: CHATML | LLAMA3 | GEMMA | PHI3
    "$HF/ORG/REPO/resolve/main/FILE.gguf",  // url — прямая ссылка на GGUF
    "FILE.gguf",                      // fileName — РОВНО последняя часть URL
    1200,                             // sizeMb — размер в МБ (должен совпадать с реальным!)
    4.0,                              // minRamGb — минимум RAM для телефона
    3,                                // russian — 1..3, насколько хорошо по-русски
    contextSize = 4096,               // окно контекста (по умолчанию 2048)
    smart = 55,                       // «ум» 0..100 (своя оценка по бенчмаркам)
    errorRate = 30,                   // % неточных ответов 0..100
    year = 2025,                      // год модели (показывается бейджем 🆕)
    vision = false,                   // true — мультимодальная (видит картинки)
    systemSuffix = "",                // например " /no_think" для Qwen3
    isNew = true,                     // показывать бейдж «новинка»
)
```

**Важно:**
- `fileName` обязан совпадать с именем файла в URL — в это имя файл ляжет в память приложения;
- `sizeMb` — реальный размер в МБ: по нему проверяется свободное место (нужно `sizeMb + 200` МБ)
  и считается прогресс;
- `format` выбирай по модели: Qwen/Gemma/LFM2 → `CHATML`, Llama → `LLAMA3`,
  Gemma → `GEMMA`, Phi → `PHI3` (смотри, какой формат у соседних похожих моделей в списке);
- архитектура только **arm64** (все современные телефоны), llama.cpp в сборке уже подключён.

## 3. (Необязательно) Модель без интернета

Можно просто положить файл руками через USB-провод в:

```
Android/data/com.school.hub/files/models/FILE.gguf
```

Приложение увидит его как «Скачано», если размер ≥ 80% от `sizeMb` в каталоге.
Поэтому `sizeMb` всё равно должен быть указан честно.

## 4. Собери приложение

**Через GitHub Actions (как делается обычно):**
1. закоммить и запушь изменения в `main`;
2. Actions сам собирает `Build APK` (~4 мин);
3. скачай `SchoolHub.apk` из релиза `build-N` (Releases репозитория).

**Локально (если есть Android SDK + NDK):**

```bash
gradle assembleDebug -PwithLlama=true
# результат: app/build/outputs/apk/debug/app-debug.apk
```

`-PwithLlama=false` — собрать быстрее без ИИ-движка (тогда чат не заработает).

## 5. Проверь

Приложение → **ИИ офлайн** → карточка новой модели → «Скачать N МБ» → после скачивания
«Открыть чат». Если модель не качается — приложение само повторит через зеркало и
напишет причину человеческим языком.

---

## Где что лежит (все исходники в этом архиве)

| Путь | Что это |
|---|---|
| `app/src/main/java/com/school/hub/feature/ai/models/Models.kt` | каталог моделей + скачивание (здесь править) |
| `app/src/main/java/com/school/hub/feature/ai/engine/LlmEngine.kt` | движок llama.cpp, форматы чата |
| `app/src/main/java/com/school/hub/feature/ai/ui/AiModelsScreen.kt` | экран моделей (карточки, кнопки) |
| `app/src/main/cpp/` | нативная сборка llama.cpp (CMake) |
| `app/src/main/java/com/school/hub/sync/MqttSync.kt` | бесплатная синхронизация через MQTT |
| `app/src/main/java/com/school/hub/` | весь остальной код приложения (Kotlin + Compose) |
| `server/` | необязательный свой REST-сервер синхронизации (Node 18+) |
| `.github/workflows/build-apk.yml` | сборка APK на GitHub Actions |
| `MODELS-URL.txt` | все ссылки на модели + зеркало + брокеры |
