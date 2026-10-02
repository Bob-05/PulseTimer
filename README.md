# PulseTimer

Интервальный таймер для тренировок. Работает полностью офлайн: данные не покидают устройство.

**Приложение бесплатное.** Без рекламы, без подписок, без встроенных покупок.

![Android](https://img.shields.io/badge/Android-9%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9%2B-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)

---

## Содержание

- [Возможности](#возможности)
- [Что приложение НЕ делает](#что-приложение-не-делает)
- [Согласие с документами](#согласие-с-документами)
- [Требования](#требования)
- [Сборка](#сборка)
- [Структура проекта](#структура-проекта)
- [Разрешения](#разрешения)
- [Технологии](#технологии)
- [Архитектура](#архитектура)
- [Модель данных](#модель-данных)
- [TimerService](#timerservice)
- [Навигация](#навигация)
- [Сборка и зависимости](#сборка-и-зависимости)
- [Известные ограничения](#известные-ограничения)
- [Тестирование](#тестирование)
- [Приватность и данные](#приватность-и-данные)
- [Лицензия](#лицензия)
- [Документы](#документы)

---

## Возможности

- **Шаблоны тренировок** — создание, редактирование, удаление
- **Интервалы** — работа / отдых / произвольные фазы с индивидуальной длительностью, цветом, эмодзи
- **Фоновые изображения и видео** — для каждого интервала или для всей тренировки
- **Своя музыка** — для интервала или общая фоновая
- **Звуковые сигналы** — 6 встроенных тембров (зуммер, свисток, гонг, двойной импульс, цифровой, колокольчик) и **свой сигнал** из аудиофайла с выбором фрагмента (начало и длительность до 1 сек)
- **Голосовые подсказки** — системный TTS объявляет названия интервалов и отсчёт 3–2–1
- **Вибрация** — 4 профиля: выкл, стандартный, интенсивный, нарастающий
- **Работа в фоне** — foreground-сервис с уведомлением, таймер идёт даже при выключенном экране
- **История тренировок** — сохраняется автоматически
- **Темы** — светлая, OLED, серая
- **Анимация смены интервалов** — 7 типов переходов: слайд, затухание, масштаб, скольжение, отскок, глубина, пружинный подъём
- **Обучение при первом запуске** — 5 страниц с прогресс-баром, мини-визуализациями и навигацией «Назад / Далее»
- **Просмотр документов в приложении** — Политика и Соглашение открываются во встроенном просмотрщике; помимо этого доступна кнопка «Открыть на GitHub»

## Что приложение НЕ делает

- Не отправляет данные на серверы
- Не содержит рекламы, аналитики, трекеров, SDK соцсетей
- Не запрашивает разрешение `INTERNET` (его физически нет в манифесте)
- Не требует регистрации и аккаунта
- Не собирает email, телефон, местоположение, контакты
- **Не участвует в стандартном системном резервном копировании** (`android:allowBackup="false"` + `dataExtractionRules`)

## Согласие с документами

При **первом запуске** приложение показывает экран согласия с Политикой конфиденциальности и Пользовательским соглашением. Оба документа можно открыть во встроенном просмотрщике до принятия. Пока согласие не получено, доступ к функциям приложения заблокирован; при отказе приложение закрывается.

Повторно открыть документы можно из `Настройки → Документы`.

## Требования

- **Android 9 (API 28)** или новее
- Разрешение на уведомления (Android 13+, запрашивается после принятия согласия)
- Для голосовых подсказок — установленный системный TTS-движок (в большинстве прошивок есть по умолчанию)

## Сборка

```bash
git clone https://github.com/Bob-05/PulseTimer.git
cd PulseTimer
./gradlew assembleDebug
```

APK появится в `app/build/outputs/apk/debug/app-debug.apk`.

Для релизной сборки — `./gradlew assembleRelease` (потребуется настроить подпись в `build.gradle.kts` или через Android Studio → Build → Generate Signed Bundle / APK).

Задача `copyLegalDocs` копирует `PRIVACY_POLICY.md` и `TERMS_OF_USE.md` в `app/build/generated/legalAssets/legal/`. Gradle подключает этот сгенерированный каталог как источник assets при сборке; вручную копировать документы в `src/main/assets/` не нужно.

## Структура проекта

```
app/src/main/java/com/pulsetimer/
├── MainActivity.kt                    # Хост навигации, запрос POST_NOTIFICATIONS,
│                                      # прогрев TTS, анимация запуска
├── data/
│   ├── AppSettings.kt                 # Хранилище настроек (SharedPreferences)
│   ├── dao/TimerDao.kt                # Room DAO
│   ├── database/AppDatabase.kt        # Room БД + миграции + предзаполнение
│   └── entity/                        # TemplateEntity, IntervalEntity, SessionLogEntity
├── service/
│   └── TimerService.kt                # Foreground-сервис таймера
├── speech/
│   └── SpeechEngine.kt                # Синглтон TextToSpeech на весь процесс
├── ui/
│   ├── component/
│   │   ├── CustomSignalEditorDialog.kt  # Диалог выбора фрагмента сигнала
│   │   └── SignalPreviewPlayer.kt       # Превью сигналов в настройках
│   ├── navigation/Screen.kt
│   ├── screen/                        # LegalConsent, LegalDocument, LaunchAnimation,
│   │                                  # Onboarding, Main, Execution, Editor,
│   │                                  # Settings, WorkoutEmojiPicker
│   └── theme/                         # Theme.kt, Type.kt
├── util/
│   ├── MarkdownRenderer.kt            # Markdown → HTML для встроенного просмотрщика
│   └── ToneGenerator.kt               # Генерация PCM-сигналов
└── viewmodel/
    └── TimerViewModel.kt              # Связь UI ↔ БД ↔ сервис
```

```
app/src/main/res/
├── drawable/
│   ├── ic_launcher_background.xml
│   ├── ic_launcher_foreground.xml
│   ├── ic_launcher_monochrome.xml
│   └── ic_splash.xml
├── mipmap-anydpi-v26/
│   ├── ic_launcher.xml
│   └── ic_launcher_round.xml
├── mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/
│   ├── ic_launcher.webp
│   └── ic_launcher_round.webp
├── xml/
│   └── data_extraction_rules.xml   # Запрет backup и device transfer на Android 12+
├── values/
│   ├── strings.xml
│   └── themes.xml
```

## Разрешения

| Разрешение | Зачем |
|---|---|
| `FOREGROUND_SERVICE` | Таймер работает в фоне |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Категория для Android 14+ |
| `WAKE_LOCK` | Не давать процессору уснуть во время тренировки |
| `VIBRATE` | Тактильный отклик при смене интервалов |
| `POST_NOTIFICATIONS` | Карточка таймера в шторке (Android 13+) |

Разрешение `INTERNET` **не запрашивается**.

### Runtime-разрешения

| Разрешение | Когда запрашивается | Последствия отказа |
|---|---|---|
| `POST_NOTIFICATIONS` | После принятия экрана согласия (Android 13+) | Нет карточки таймера в шторке, сервис работает |

Разрешения на чтение/запись внешнего хранилища (`READ_EXTERNAL_STORAGE`, `READ_MEDIA_*` и др.) **не запрашиваются**. Доступ к выбранным пользователем файлам (изображение, видео, аудио) осуществляется через SAF (`ActivityResultContracts.OpenDocument`) — системный диалог выбора файла, выдающий URI-доступ per-file. Через `takePersistableUriPermission` доступ сохраняется между запусками. Приложение не сканирует хранилище и не получает доступ к файлам за пределами явно выбранных пользователем.

### Foreground Service

`foregroundServiceType="specialUse"` с обоснованием в `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` = `"Keeps an active interval timer running during a workout"`. Это требование Android 14+ для сервисов, не попадающих под стандартные категории.

### Резервное копирование

В манифесте:
- `android:allowBackup="false"` — отключает backup на Android ≤11;
- `android:dataExtractionRules="@xml/data_extraction_rules"` — отключает cloud backup и device transfer на Android 12+.

Правила лежат в `app/src/main/res/xml/data_extraction_rules.xml` и исключают все домены (`root`, `file`, `database`, `sharedpref`, `external`).

## Технологии

- Kotlin + Coroutines / Flow
- Jetpack Compose + Material 3
- Navigation Compose
- Room (SQLite)
- Foreground Service + Notification
- MediaPlayer / AudioTrack / TextToSpeech / Vibrator
- WebView (для отображения Политики и Соглашения из локальных assets)
- Собственный мини-рендерер Markdown → HTML (`util/MarkdownRenderer.kt`)

---

## Архитектура

```
┌───────────────────────────────────────────────────┐
│  UI Layer (Compose)                               │
│  LegalConsentScreen, LegalDocumentScreen,         │
│  LaunchAnimationScreen, OnboardingScreen,         │
│  MainScreen, ExecutionScreen,                     │
│  EditorScreen, SettingsScreen                     │
└───────────────────┬───────────────────────────────┘
                    │ StateFlow / collectAsState
┌───────────────────▼───────────────────────────────┐
│  TimerViewModel (AndroidViewModel)                │
│  - templates, sessionLogs, selectedTemplate       │
│  - timerState (проксирует TimerService.state)     │
│  - hasIntervals(templateId) — проверка перед стартом
└──────┬───────────────────────────────┬────────────┘
       │ Room queries                  │ Intent (startForegroundService)
┌──────▼──────────────┐        ┌───────▼────────────┐
│  AppDatabase        │        │  TimerService      │
│  (templates,        │        │  - state Flow      │
│   intervals,        │        │  - audio / TTS     │
│   session_logs)     │        │  - vibrator        │
└─────────────────────┘        │  - wake lock       │
                               └─────────┬──────────┘
                                         │
                               ┌─────────▼──────────┐
                               │  SpeechEngine      │
                               │  (singleton TTS)   │
                               └────────────────────┘
```

`TimerService` не привязан через `bindService`. Состояние передаётся через статический `MutableStateFlow` в `companion object`. UI подписывается на него, сервис обновляет. Состояние живёт до смерти процесса.

`SpeechEngine` — синглтон на весь процесс: `MainActivity` прогревает TTS заранее, `TimerService` переиспользует готовый инстанс.

---

## Модель данных

### Room: `pulse_timer_database`, version = 3

#### `templates`

| Поле | Тип | Описание |
|---|---|---|
| id | Long (PK) | Автогенерируется |
| name | String | Название |
| description | String | Описание |
| createdAt | Long | Timestamp создания |
| iconEmoji | String | Эмодзи (по умолчанию 🏋️) |
| backgroundType | String | `COLOR` / `CUSTOM_IMAGE` / `VIDEO` |
| backgroundValue | String | URI или пусто |
| audioUri | String? | URI музыки |
| vibrationPatternId | Int | 0–3 |

#### `intervals`

| Поле | Тип | Описание |
|---|---|---|
| id | Long (PK) | Автогенерируется |
| templateId | Long (FK, CASCADE) | Родительский шаблон |
| name | String | Название фазы |
| durationSeconds | Int | Длительность |
| colorHex | String | Цвет фона |
| orderIndex | Int | Порядок |
| iconEmoji | String | Эмодзи интервала |
| backgroundType | String | Аналогично template |
| backgroundValue | String | URI или пусто |
| audioUri | String? | URI музыки интервала |
| vibrationPatternId | Int | 0–3 |

#### `session_logs`

| Поле | Тип | Описание |
|---|---|---|
| id | Long (PK) | Автогенерируется |
| templateId | Long (FK, CASCADE) | Шаблон |
| templateName | String | Копия имени на момент запуска |
| startedAt | Long | Начало |
| completedAt | Long? | Конец (`null` — прервана) |
| totalDurationSeconds | Int | Фактическая длительность |

**Миграции:** `1→2` (фоны, аудио, вибрация), `2→3` (эмодзи). Обе — `ALTER TABLE ADD COLUMN`.

**Предзаполнение:** при первом создании БД добавляются 2 шаблона — «Табата» (20/10×8) и «Круговая» (30/30×3).

### SharedPreferences: `pulse_timer_settings`

Все настройки в одном файле через `AppSettingsStore` (singleton с `synchronized`).

| Ключ | Тип | По умолчанию |
|---|---|---|
| soundEnabled | Boolean | true |
| voiceEnabled | Boolean | true |
| soundVolume | Float | 0.8 |
| toneType | String | CLASSIC |
| customSignalUri | String? | null |
| customSignalStartMs | Int | 0 |
| vibrationEnabled | Boolean | true |
| vibrationProfile | String | SPORT |
| theme | String | OLED |
| animatedBackgrounds | Boolean | true |
| intervalAnimation | String | SLIDE |
| musicUri | String? | null |
| onboardingCompleted | Boolean | false |
| legalConsentAccepted | Boolean | false |

---

## TimerService

### Жизненный цикл

1. `onCreate()` — создаёт канал уведомлений, `WakeLock`, прогревает `SpeechEngine` (если голос включён).
2. `onStartCommand(intent)` — обрабатывает ACTION. Первое, что делает: `startForeground(...)` (обязательно в течение 5 секунд).
3. `startTimer(templateId)` — параллельно грузит `template` и `intervals` из Room (`async/await`), запускает первый интервал.
4. `runInterval()` запускает общий цикл `startCountdownLoop()` для отсчёта времени. Последние 3 секунды интервала: тон + вибрация + TTS.
5. При завершении всех интервалов → `finishWorkout()` → запись в `session_logs` → `stopForeground` + `stopSelf`.
6. `onDestroy()` — сохраняет активную (не завершённую) сессию как брошенную, отменяет `CoroutineScope`, релизит `WakeLock`, `MediaPlayer`, `AudioTrack`, снимает listener с `SpeechEngine`.

### Управление

- ACTION: `START`, `PAUSE`, `RESUME`, `SKIP`, `PREVIOUS`, `STOP`.
- Из UI — через `TimerViewModel`, который шлёт `Intent` в сервис.
- Из уведомления — через `PendingIntent` с теми же ACTION.

### Уведомление

- Канал `pulse_timer_channel`, importance = `LOW`, без звука и вибрации.
- Три action-кнопки: Пауза/Продолжить, Пропустить, Стоп.
- Обновляется при каждом тике (`updateNotification()`).
- На Android 13+ без разрешения `POST_NOTIFICATIONS` не отображается — сервис продолжает работать.

### Аудио

- **Сигналы** — синтезируются в `AudioTrack` (PCM 16-bit, 44.1 kHz, моно). Встроенные тембры: зуммер (880 Гц), свисток (частота растёт 1400→2200 Гц), гонг (сумма 420 + 630 + 1050 Гц с decay), двойной импульс, цифровой сигнал (три ноты), колокольчик. **Свой сигнал** — воспроизводится через `MediaPlayer` с `seekTo` на выбранный фрагмент и авто-стопом на 1 сек.
- **Музыка** — `MediaPlayer`, зацикливается, громкость из настроек. При сигнале громкость временно снижается до 0.5 и возвращается.
- **TTS** — системный движок, locale из системы. Работает через общий `SpeechEngine`. Если `voiceEnabled = false` — не вызывается. У некоторых вендоров TTS может работать через облако (см. Политику, п. 5.1).

### Вибрация

Четыре профиля:

- `0` — выкл
- `1` — стандартный (200 мс ×2 для работы, 500 мс для отдыха)
- `2` — интенсивный (400 мс ×3)
- `3` — нарастающий (100 → 180 → 320 мс)

Сила амплитуды — из `vibrationProfile` (`SOFT` = 80, `SPORT` = 170, `EXTREME` = 255).

---

## Навигация

```
LegalConsent (первый запуск, обязательный)
   └→ Onboarding (5 страниц с прогресс-баром, если не пройден)
       └→ Main
           ├→ Execution/{templateId}/{templateName}
           ├→ Editor/{templateId}
           ├→ Settings
           │   ├→ Onboarding (повторный показ)
           │   └→ LegalDocument/{type}   (type = privacy | terms)
```

При завершении тренировки из Execution: `navigate(Main) { popUpTo(Main) { inclusive = true } }` — очищает стек, чтобы Main был один.

---

## Сборка и зависимости

Управление версиями — через version catalog (`gradle/libs.versions.toml`).

**minSdk 28** (Android 9), **targetSdk 36**. `versionCode = 1`, `versionName = "1.0"`.

**Ключевые зависимости:** Room, Compose BOM, Material 3, Navigation Compose, Lifecycle, Coroutines.

**R8:** отключён (`isMinifyEnabled = false`). При включении потребуются `-keep` правила для Room entities и DAO.

---

## Известные ограничения

1. **Состояние сервиса в статическом Flow** — при убийстве процесса система может пересоздать сервис, но `state` обнулится. Автовосстановления тренировки нет.
2. **Гонка при удалении интервалов** — если удалить интервалы между проверкой в UI и запуском сервиса, `ExecutionScreen` покажет экран ошибки через 6 секунд тайм-аута.
3. **TTS может быть облачным** — у некоторых вендоров (например, Google для отдельных языков) синтез речи выполняется на серверах. Разработчик это не контролирует.
4. **Резервное копирование отключено** — `android:allowBackup="false"` и `dataExtractionRules`. Переустановка приложения = потеря данных.
5. **Ручной экспорт/импорт шаблонов не предусмотрен.**
6. **Точность таймера** — расчёт оставшегося времени привязан к `SystemClock.elapsedRealtime()`, накопления дрейфа нет. При агрессивной экономии батареи возможно смещение тика в пределах ±100 мс.
7. **Кастомный Markdown-рендерер** — поддерживает только те элементы разметки, которые используются в текущих документах. При появлении более сложной разметки (вложенные списки, сноски, definition lists) рекомендуется перейти на `org.commonmark:commonmark`.

---

## Тестирование

Есть instrumentation-тест `SessionHistoryTest`, проверяющий запись, обновление и удаление истории тренировок в Room. Unit-тестов и широкого покрытия UI пока нет.

Ручной чек-лист:

- [ ] Первый запуск → экран согласия → открыть политику и условия из встроенного просмотрщика → закрыть → принять → онбординг
- [ ] Первый запуск → экран согласия → отказ → приложение закрывается
- [ ] Онбординг: кнопка «Пропустить» в верхнем баре срабатывает
- [ ] Онбординг: кнопка «Назад» появляется со 2-й страницы и возвращает назад
- [ ] Онбординг: прогресс-бар и счётчик «Шаг X из Y» обновляются
- [ ] Онбординг: у каждой страницы уникальная мини-визуализация
- [ ] Создание пустой тренировки → СТАРТ → Snackbar «нет интервалов» с кнопкой «Редактировать»
- [ ] Создание тренировки с интервалами → СТАРТ → таймер идёт
- [ ] Уведомление показывает кнопки Пауза / Пропустить / Стоп
- [ ] Выключение экрана — таймер продолжает идти
- [ ] Голосовые подсказки работают
- [ ] Вибрация срабатывает
- [ ] История сохраняется после завершения
- [ ] Отзыв разрешения уведомлений → настройки показывают красную карточку
- [ ] Свайп влево/вправо переключает интервалы
- [ ] Все 7 типов анимаций перехода работают
- [ ] Выбор своего сигнала: открыть диалог, перемотать слайдер, прослушать фрагмент, сохранить
- [ ] Карточка шаблона с длинным описанием → «Показать полностью» открывает модальное окно
- [ ] Карточки шаблонов имеют одинаковый размер
- [ ] В просмотрщике документов корректно отображаются маркированные списки, таблицы и inline-код

---

## Приватность и данные

Приложение **не собирает и не передаёт данные** на серверы разработчика:

- ❌ Нет разрешения `INTERNET` — сетевые соединения физически невозможны
- ❌ Нет SDK аналитики, рекламы, трекинга, отчётности о сбоях
- ✅ Все данные хранятся локально в Room (SQLite) и SharedPreferences
- ✅ `android:allowBackup="false"` + `dataExtractionRules` — системное резервное копирование отключено на всех версиях Android
- ✅ Доступ к файлам — только через SAF по явному выбору пользователя, без runtime-разрешений на хранилище
- ⚠️ Голосовые подсказки озвучиваются системным TTS-движком, который у некоторых вендоров может работать через облако

Подробнее:

- [Политика конфиденциальности](PRIVACY_POLICY.md)
- [Пользовательское соглашение](TERMS_OF_USE.md)

---

## Лицензия

Распространяется под лицензией [Apache License, Version 2.0](LICENSE).

Copyright (c) 2026 Bob-05.

## Документы

- [Политика конфиденциальности](PRIVACY_POLICY.md)
- [Пользовательское соглашение](TERMS_OF_USE.md)