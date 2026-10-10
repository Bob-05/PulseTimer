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
 *   У шага-перехода подпись обязательна: иначе кнопка «Далее» нажимала бы элемент
 *   за пользователя, ничем не отличаясь от обычного перехода между подсказками.
 * @param scrimAlpha своя прозрачность затемнения на этом шаге; `null` — общая для
 *   всего обучения. Экран идущей тренировки просит затемнение светлее, иначе за
 *   ним не разглядеть бегущий таймер.
 */
data class TutorialStep(
    val targetKey: String?,
    val emoji: String? = null,
    val title: String,
    val text: String,
    val pressTarget: Boolean = false,
    val actionLabel: String? = null,
    val scrimAlpha: Float? = null
) {
    init {
        // Невалидное состояние ловим при создании шага, а не при показе: набор
        // шагов — это данные, и падать на них лучше в тесте, чем в подсказке.
        require(!pressTarget || actionLabel != null) {
            "Шаг-переход «$title» должен нести подпись кнопки (actionLabel)"
        }
    }
}

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

    /**
     * Финальный шаг: подсказка по центру экрана, без привязки к элементу.
     *
     * Тур доходит до идущей тренировки, поэтому и здесь затемнение светлее
     * ([LIGHT_TUTORIAL_SCRIM_ALPHA]): за подсказкой должен быть виден таймер.
     */
    private val tourEnd = TutorialStep(
        targetKey = null,
        emoji = "✅",
        title = "Готово!",
        text = "Отсчёт продолжается: тренировку можно вести по часам или по " +
            "голосовым подсказкам. Когда интервалы закончатся, появится экран " +
            "результатов, а запись попадёт в «Настройки → История тренировок». " +
            "Повторить обучение можно там же, в разделе «Справка».",
        scrimAlpha = LIGHT_TUTORIAL_SCRIM_ALPHA
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
                text = "Если что-то забудется, обучение можно запустить заново " +
                    "отсюда: тур начнётся с главного экрана и пройдёт по всем разделам."
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
        // --- Идущая тренировка: отсчёт, перелистывание, пауза, завершение ---
        addAll(workoutProcessSteps())
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

    /** Экран идущей тренировки: отсчёт и управление, таймер при этом не встаёт. */
    val EXECUTION_SCREEN: List<TutorialStep> = buildList {
        add(
            TutorialStep(
                targetKey = null,
                emoji = "🏃",
                title = "Тренировка идёт",
                text = "Покажу, как вести тренировку и что делать между интервалами. " +
                    "Обучение не останавливает отсчёт — можно двигаться дальше или " +
                    "сразу нажать «Пропустить».",
                scrimAlpha = LIGHT_TUTORIAL_SCRIM_ALPHA
            )
        )
        addAll(workoutProcessSteps())
    }

    /**
     * Шаги про саму тренировку: отсчёт, перелистывание интервалов, управление,
     * пауза и завершение.
     *
     * Набор общий для сквозного тура и для подсказок при первом заходе на экран
     * отсчёта: процесс тренировки должен рассказываться одинаково. Два шага —
     * переходы: обучение само «нажимает» паузу, а следующим шагом возвращает
     * отсчёт, поэтому паузу пользователь не только читает, но и видит.
     *
     * Затемнение тут светлее ([LIGHT_TUTORIAL_SCRIM_ALPHA]): таймер должен
     * оставаться различимым под подсказкой.
     */
    private fun workoutProcessSteps(): List<TutorialStep> = listOf(
        TutorialStep(
            targetKey = TutorialTargets.EXECUTION_TIMER,
            emoji = "⏱",
            title = "Идёт отсчёт",
            text = "Название интервала, его номер и время до конца. Смена интервала " +
                "сопровождается сигналом, вибрацией и голосовой подсказкой, а сам " +
                "отсчёт не останавливается даже при выключенном экране.",
            scrimAlpha = LIGHT_TUTORIAL_SCRIM_ALPHA
        ),
        TutorialStep(
            targetKey = TutorialTargets.EXECUTION_SWIPE,
            emoji = "👉",
            title = "Перелистывание интервалов",
            text = "Свайп влево пропускает интервал, вправо — возвращает предыдущий. " +
                "Смена идёт с выбранным переходом: меняются цвет, фон, мелодия и " +
                "видео, если оно задано у интервала.",
            scrimAlpha = LIGHT_TUTORIAL_SCRIM_ALPHA
        ),
        TutorialStep(
            targetKey = TutorialTargets.EXECUTION_CONTROLS,
            emoji = "⏯",
            title = "Управление",
            text = "Слева — возврат к предыдущему интервалу, в центре — пауза, " +
                "справа — «Пропустить». Те же кнопки есть в карточке уведомления, " +
                "так что тренировкой можно управлять с выключенным экраном.",
            scrimAlpha = LIGHT_TUTORIAL_SCRIM_ALPHA
        ),
        TutorialStep(
            targetKey = TutorialTargets.EXECUTION_PAUSE,
            emoji = "⏸",
            title = "Проверим паузу",
            text = "Нажму паузу за вас: отсчёт остановится, а оставшееся время " +
                "интервала сохранится — на паузе можно подтянуть ремни или допить " +
                "воду.",
            pressTarget = true,
            actionLabel = "Пауза",
            scrimAlpha = LIGHT_TUTORIAL_SCRIM_ALPHA
        ),
        TutorialStep(
            targetKey = TutorialTargets.EXECUTION_RESUME,
            emoji = "▶️",
            title = "Продолжаем отсчёт",
            text = "Возвращаю отсчёт: время интервала не сбросилось — таймер " +
                "продолжает с той же секунды.",
            pressTarget = true,
            actionLabel = "Продолжить",
            scrimAlpha = LIGHT_TUTORIAL_SCRIM_ALPHA
        ),
        TutorialStep(
            targetKey = TutorialTargets.EXECUTION_CLOSE,
            emoji = "✕",
            title = "Завершение тренировки",
            text = "«✕» в углу останавливает тренировку и сохраняет её: план, факт и " +
                "пропуски потом видно в «Настройки → История тренировок».",
            scrimAlpha = LIGHT_TUTORIAL_SCRIM_ALPHA
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

