package com.pulsetimer.ui.tutorial

import com.pulsetimer.data.AppSettings

/**
 * Шаг обучения.
 *
 * `targetKey == null` — шаг без привязки к элементу: подсказка показывается
 * по центру экрана, без «дырки» в затемнении. Так делаются вводный и
 * итоговый шаги, а также рассказ о том, что видно на других экранах.
 *
 * @param emoji значок в кружке рядом с заголовком; `null` — подсказка без значка.
 * @param pressTarget шаг-переход: перед ним обучение прокручивает экран к цели,
 *   а по кнопке «Далее» само «нажимает» её, чтобы провести пользователя на
 *   другой экран (открыть редактор, настройки, запустить тренировку).
 * @param actionLabel подпись основной кнопки на шаге-переходе; `null` — «Далее».
 */
data class TutorialStep(
    val targetKey: String?,
    val emoji: String? = null,
    val title: String,
    val text: String,
    val pressTarget: Boolean = false,
    val actionLabel: String? = null
)

/**
 * Экран, которому принадлежит шаг: префикс ключа цели (`main.`, `settings.`…).
 * Пустая строка — шаг по центру, без привязки к экрану.
 *
 * По нему обучение решает, доступна ли кнопка «Назад»: возврат на предыдущий
 * экран тур не выполняет, поэтому шаг назад предлагается только внутри одного
 * экрана.
 */
internal val TutorialStep.screenPrefix: String
    get() = targetKey?.substringBefore('.')?.let { prefix -> "$prefix." } ?: ""


/**
 * Тексты интерактивного обучения.
 *
 * Основной набор — [guidedTour]: сквозной тур, который сам ведёт пользователя
 * с главного экрана в редактор, настройки и идущую тренировку. Дополнительные
 * наборы ([SETTINGS_SCREEN], [EDITOR_SCREEN], [EXECUTION_SCREEN]) показываются
 * при первом заходе на экран, если тур его не затронул (см. [TutorialTrigger]).
 */
object TutorialSteps {

    /** Финальный шаг: подсказка по центру экрана, без привязки к элементу. */
    private val tourEnd = TutorialStep(
        targetKey = null,
        emoji = "✅",
        title = "Готово!",
        text = "Результаты сохраняются в «Настройки → История тренировок»: нажмите " +
            "на запись, чтобы увидеть разбор по интервалам. Повторить обучение можно " +
            "там же, в разделе «Справка»."
    )

    /**
     * Сквозной тур: обучение само прокручивает экраны, раскрывает группы
     * настроек и «нажимает» нужные кнопки, чтобы показать приложение целиком.
     *
     * @param hasWorkouts есть ли хотя бы одна тренировка: без неё показывать
     *   редактор и запуск нечего — тур ограничивается главным экраном.
     */
    fun guidedTour(hasWorkouts: Boolean): List<TutorialStep> = buildList {
        add(
            TutorialStep(
                targetKey = null,
                emoji = "👋",
                title = "Добро пожаловать в PulseTimer",
                text = "Проведу по всему приложению: покажу тренировки, запуск, " +
                    "редактор интервалов и настройки. Обучение можно пропустить " +
                    "в любой момент."
            )
        )
        add(
            TutorialStep(
                targetKey = TutorialTargets.ADD_TEMPLATE,
                emoji = "➕",
                title = "Создайте тренировку",
                text = "Кнопка «Добавить тренировку» добавляет карточку в начало " +
                    "списка. Название, значок и описание потом меняются в редакторе."
            )
        )
        if (!hasWorkouts) {
            add(
                TutorialStep(
                    targetKey = null,
                    emoji = "🎴",
                    title = "Карточек пока нет",
                    text = "Как только появится первая тренировка, покажу её карточку, " +
                        "редактор интервалов, настройки и запуск отсчёта."
                )
            )
            add(tourEnd)
            return@buildList
        }

        // --- Главный экран: карточка, её действия и переход в редактор ---
        add(
            TutorialStep(
                targetKey = TutorialTargets.CARD,
                emoji = "🎴",
                title = "Карточка тренировки",
                text = "Тренировка состоит из интервалов: у каждого своё название, " +
                    "длительность, цвет, фон и мелодия. Карточки листаются свайпом."
            )
        )
        add(
            TutorialStep(
                targetKey = TutorialTargets.CARD_ACTIONS,
                emoji = "↕️",
                title = "Порядок и действия",
                text = "Стрелки ↑ и ↓ меняют порядок тренировок, карандаш открывает " +
                    "редактор, а корзина удаляет тренировку вместе с её историей."
            )
        )
        add(
            TutorialStep(
                targetKey = TutorialTargets.CARD_EDIT,
                emoji = "🛠",
                title = "Откроем редактор",
                text = "Карандаш на карточке открывает редактор: покажу, как " +
                    "собираются интервалы. Нажму карандаш за вас.",
                pressTarget = true,
                actionLabel = "Открыть редактор"
            )
        )
        // --- Редактор: параметры тренировки и интервалы ---
        add(
            TutorialStep(
                targetKey = TutorialTargets.EDITOR_TEMPLATE,
                emoji = "🎛",
                title = "Параметры тренировки",
                text = "Значок, название, описание, фон (картинка или видео) и общая " +
                    "музыка всей тренировки. Кнопки «Сохранить» нет — пишется сразу."
            )
        )
        add(
            TutorialStep(
                targetKey = TutorialTargets.EDITOR_INTERVAL,
                emoji = "🧱",
                title = "Интервалы",
                text = "Каждая карточка — один интервал: длительность, цвет, свой фон, " +
                    "мелодия и вибрация."
            )
        )
        add(
            TutorialStep(
                targetKey = TutorialTargets.EDITOR_ADD_INTERVAL,
                emoji = "➕",
                title = "Новый интервал",
                text = "Кнопка «+» добавляет интервал, а стрелки и значок копирования " +
                    "на карточке правят порядок и дублируют настройки."
            )
        )
        add(
            TutorialStep(
                targetKey = TutorialTargets.EDITOR_BACK,
                emoji = "↩️",
                title = "Вернёмся к списку",
                text = "Редактор посмотрели — возвращаюсь на главный экран.",
                pressTarget = true,
                actionLabel = "К списку тренировок"
            )
        )

        // --- Настройки: раскроем нужные группы и вернёмся назад ---
        add(
            TutorialStep(
                targetKey = TutorialTargets.SETTINGS_ICON,
                emoji = "⚙️",
                title = "Заглянем в настройки",
                text = "В шапке живут настройки: звук, вибрация, тема и история " +
                    "тренировок. Открою их за вас.",
                pressTarget = true,
                actionLabel = "Открыть настройки"
            )
        )
        add(
            TutorialStep(
                targetKey = TutorialTargets.SETTINGS_APP_GROUP,
                emoji = "🔔",
                title = "Настройки приложения",
                text = "Уведомления о ходе тренировки, тема оформления и анимации " +
                    "интерфейса. Группу раскрыл — её можно свернуть нажатием."
            )
        )
        add(
            TutorialStep(
                targetKey = TutorialTargets.SETTINGS_WORKOUT_GROUP,
                emoji = "🔊",
                title = "Настройки тренировок",
                text = "Звуковые сигналы, голосовой помощник, громкость и вибрация — " +
                    "в том числе свои аудиофайлы вместо стандартных сигналов."
            )
        )
        add(
            TutorialStep(
                targetKey = TutorialTargets.SETTINGS_HISTORY,
                emoji = "🗂",
                title = "История тренировок",
                text = "Здесь копятся завершённые тренировки: нажмите на запись — " +
                    "откроется разбор по интервалам: план, факт и пропуски."
            )
        )
        add(
            TutorialStep(
                targetKey = TutorialTargets.SETTINGS_TUTORIAL,
                emoji = "🎯",
                title = "Повтор обучения",
                text = "Если что-то забудется, запустите обучение заново отсюда — " +
                    "целиком или по конкретному экрану."
            )
        )
        add(
            TutorialStep(
                targetKey = TutorialTargets.SETTINGS_BACK,
                emoji = "↩️",
                title = "Возвращаемся",
                text = "Настройки посмотрели — иду обратно к тренировкам.",
                pressTarget = true,
                actionLabel = "К тренировкам"
            )
        )

        // --- Запуск тренировки и экран отсчёта ---
        add(
            TutorialStep(
                targetKey = TutorialTargets.START,
                emoji = "▶️",
                title = "Запустим тренировку",
                text = "Кнопка «СТАРТ» включает отсчёт — запущу первую тренировку. " +
                    "Таймер продолжает идти даже при выключенном экране.",
                pressTarget = true,
                actionLabel = "Начать тренировку"
            )
        )
        add(
            TutorialStep(
                targetKey = TutorialTargets.EXECUTION_TIMER,
                emoji = "⏱",
                title = "Идёт отсчёт",
                text = "Название интервала, его номер и время до конца. Смена " +
                    "интервала сопровождается сигналом, вибрацией и голосом."
            )
        )
        add(
            TutorialStep(
                targetKey = TutorialTargets.EXECUTION_CONTROLS,
                emoji = "⏯",
                title = "Управление",
                text = "Пауза, «Пропустить» и возврат к предыдущему интервалу, а «✕» " +
                    "завершает тренировку и сохраняет её в историю."
            )
        )
        add(tourEnd)
    }

    /** «Настройки»: уведомления, внешний вид, тренировка и история. */
    val SETTINGS_SCREEN: List<TutorialStep> = listOf(
        TutorialStep(
            targetKey = null,
            emoji = "🎛",
            title = "Настройки",
            text = "Здесь собрано всё, что влияет на тренировки: уведомления, " +
                "внешний вид, звук и история. Пробегусь по главному."
        ),
        TutorialStep(
            targetKey = TutorialTargets.SETTINGS_NOTIFICATIONS,
            emoji = "🔔",
            title = "Уведомления",
            text = "С разрешением на уведомления прогресс и кнопки «Пауза» / «Пропустить» " +
                "доступны прямо в шторке — удобно, когда экран выключен."
        ),
        TutorialStep(
            targetKey = TutorialTargets.SETTINGS_APP_GROUP,
            emoji = "🎨",
            title = "Внешний вид",
            text = "Тема приложения (светлая, OLED, серая), анимированные фоны " +
                "и один из 7 переходов между интервалами."
        ),
        TutorialStep(
            targetKey = TutorialTargets.SETTINGS_WORKOUT_GROUP,
            emoji = "🏋️",
            title = "Настройки тренировок",
            text = "Звук сигнала и голосовой помощник, громкость, свои аудиофайлы " +
                "и профиль вибрации. Всё это можно переопределить внутри интервала."
        ),
        TutorialStep(
            targetKey = TutorialTargets.SETTINGS_HISTORY,
            emoji = "📊",
            title = "История тренировок",
            text = "Каждая тренировка сохраняется автоматически. Разверните раздел " +
                "и нажмите на запись — откроется разбор по интервалам: план, факт " +
                "и пропуски."
        ),
        TutorialStep(
            targetKey = TutorialTargets.SETTINGS_TUTORIAL,
            emoji = "🎯",
            title = "Повтор обучения",
            text = "Отсюда можно запустить это обучение заново — на главном экране, " +
                "в настройках, редакторе и на экране идущей тренировки."
        )
    )

    /** Редактор тренировки: параметры, интервалы и их действия. */
    val EDITOR_SCREEN: List<TutorialStep> = listOf(
        TutorialStep(
            targetKey = null,
            emoji = "🛠",
            title = "Редактор тренировки",
            text = "Соберём тренировку: параметры, интервалы и их порядок. " +
                "Кнопки «Сохранить» нет — изменения пишутся сразу."
        ),
        TutorialStep(
            targetKey = TutorialTargets.EDITOR_TEMPLATE,
            emoji = "🎴",
            title = "Параметры тренировки",
            text = "Значок, название, описание, фон (картинка или видео) " +
                "и общая музыка всей тренировки."
        ),
        TutorialStep(
            targetKey = TutorialTargets.EDITOR_INTERVAL,
            emoji = "🧱",
            title = "Интервалы",
            text = "Каждая карточка — один интервал: название, длительность, цвет, " +
                "свой фон, мелодия и вибрация."
        ),
        TutorialStep(
            targetKey = TutorialTargets.EDITOR_INTERVAL_ACTIONS,
            emoji = "⧉",
            title = "Правка и копирование",
            text = "Карандаш меняет интервал, значок копирования дублирует его " +
                "со всеми настройками, корзина удаляет."
        ),
        TutorialStep(
            targetKey = TutorialTargets.EDITOR_ADD_INTERVAL,
            emoji = "➕",
            title = "Добавить интервал",
            text = "Кнопка «+» внизу добавляет новый интервал в конец списка. " +
                "Дальше его можно переставить стрелками на карточке."
        ),
        TutorialStep(
            targetKey = null,
            emoji = "🚀",
            title = "Готово!",
            text = "Вернитесь на главный экран и нажмите «СТАРТ» — тренировка " +
                "запустится с текущим набором интервалов."
        )
    )

    /** Экран идущей тренировки: короткий набор, таймер при этом не останавливается. */
    val EXECUTION_SCREEN: List<TutorialStep> = listOf(
        TutorialStep(
            targetKey = null,
            emoji = "🏃",
            title = "Тренировка идёт",
            text = "Таймер продолжает работать даже при выключенном экране. " +
                "Это обучение не останавливает отсчёт — можно двигаться дальше " +
                "или сразу нажать «Пропустить»."
        ),
        TutorialStep(
            targetKey = TutorialTargets.EXECUTION_TIMER,
            emoji = "⏱",
            title = "Текущий интервал",
            text = "Название интервала, его номер и время до конца. Смена интервала " +
                "сопровождается сигналом, вибрацией и голосовой подсказкой."
        ),
        TutorialStep(
            targetKey = TutorialTargets.EXECUTION_CONTROLS,
            emoji = "⏯",
            title = "Управление",
            text = "Пауза, «Пропустить» и возврат к предыдущему интервалу. " +
                "Свайп влево/вправо листает интервалы, а «✕» завершает тренировку " +
                "и сохраняет её в историю."
        )
    )
}

/**
 * Помечает обучение пройденным: главный флаг плюс флаги тех экранов, которые
 * обучение действительно показало.
 *
 * Сквозной тур без единой тренировки не доходит до редактора и экрана отсчёта —
 * и не должен «съедать» подсказки по ним: их покажет [TutorialTrigger] при
 * первом заходе на соответствующий экран.
 */
fun AppSettings.markTourCompleted(steps: List<TutorialStep>): AppSettings {
    val screens = steps.map { step -> step.screenPrefix }.toSet()
    return copy(
        tutorialCompleted = true,
        tutorialRequested = false,
        settingsTutorialCompleted = settingsTutorialCompleted ||
            TutorialTargets.PREFIX_SETTINGS in screens,
        editorTutorialCompleted = editorTutorialCompleted ||
            TutorialTargets.PREFIX_EDITOR in screens,
        executionTutorialCompleted = executionTutorialCompleted ||
            TutorialTargets.PREFIX_EXECUTION in screens
    )
}

