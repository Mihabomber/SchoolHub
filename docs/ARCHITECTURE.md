# Архитектура SchoolHub

## Слои (MVVM + offline-first)
```
UI (Compose screens) ──observe StateFlow──▶ ViewModel ──▶ Repository ──▶ Room (истина)
                                                              ▲
                          SyncCoordinator ── CloudSync (Retrofit) ── Node.js REST
                          NearbySyncManager (Bluetooth / Wi-Fi Direct, P2P_CLUSTER)
```
DI — ручной `AppContainer` (без Hilt, чтобы сборка была проще; перейти на Hilt легко).

## Структура пакетов
```
com.school.hub
├── SchoolApp.kt / AppContainer.kt / MainActivity.kt
├── core
│   ├── data        AppDatabase, SettingsStore, ImageStorage, DeviceInfo
│   ├── ui/theme    цвета, градиенты, типографика, формы (Material 3, светлая/тёмная)
│   ├── ui/components  SubjectBadge, GradientIcon, EmptyState…
│   └── util        склонения, относительное время
├── feature
│   ├── cheatsheets
│   │   ├── model   Subject, CheatSheet, CheatSheetDraft
│   │   ├── data    Entity, Dao, Repository, SeedData
│   │   └── ui      List / Detail / Edit + ViewModel
│   ├── home        дашборд
│   └── soon        заглушки будущих модулей (+ реальная проверка RAM для LLM)
├── sync            DTO, SyncCodec, CloudSync, SyncCoordinator, NearbySyncManager, SyncScreen
└── navigation      Routes, NavHost, bottom bar, фабрика ViewModel
```

## Модель синхронизации
| Поле | Зачем |
|---|---|
| `uuid` | глобальный id шпаргалки на всех устройствах |
| `updatedAt` | Last-Writer-Wins |
| `deleted` | «надгробие»: удаление распространяется |
| `dirty` | ещё не отправлено на сервер |
| `originDevice` | метка «моя» |

Nearby: gzip-JSON снимок передаётся как FILE-payload (BYTES ограничены 32 КБ).
Фото ужимаются до 1600px/JPEG 80 и едут внутри снимка (base64).
Тай-брейк: подключение инициирует устройство с меньшим deviceId, чтобы не было встречных запросов.

## minSdk = 26 (Android 8.0)
* ML Kit Translation / Text Recognition — от 21
* Nearby Connections — от 16 (нужны Google Play services)
* llama.cpp — нужен arm64-v8a с NEON; на практике устройства с ≥3 ГБ RAM — это Android 8+
* 26 даёт `java.time`, адаптивные иконки и покрывает ~97% активных устройств

## Дальнейшие шаги
1. Расписание: Room `lessons`, `bells` + AlarmManager/WorkManager уведомления, sync тем же механизмом.
2. ДЗ: `homework` (subject, dueDate, done) + фильтры сегодня/завтра/неделя.
3. LLM: модуль `:llama` (CMake + llama.cpp JNI, arm64-v8a), DownloadManager только по Wi-Fi.
4. Переводчик: `com.google.mlkit:translate` + `text-recognition` + CameraX оверлей.
