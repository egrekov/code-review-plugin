# Code Review Helper — PhpStorm Plugin

Плагин для проведения code review прямо в PhpStorm с экспортом в формат Redmine.

## Как пользоваться

### Панель "Code Review"
Открывается внизу слева. В правом углу панели три кнопки:

| Кнопка | Действие |
|--------|----------|
| ▶ | Начать новое ревью |
| + | Добавить комментарий к выделенному коду |
| 📋 | Открыть окно с готовым отчётом |

### Процесс ревью

1. Нажми **▶** — начинается сессия ревью
2. Выдели кусок кода в редакторе
3. Нажми **Ctrl+Alt+R** или кнопку **+** в панели
4. В диалоге:
   - Поле **Reference** — автоматически заполняется (`\Namespace\Class::method`), можно поправить
   - Поле **Comment** — пиши замечание
5. Повторяй шаги 2–4 для всех замечаний
6. Нажми **📋** — откроется окно с отчётом в формате Redmine
7. Отредактируй при необходимости, нажми **Copy & Close**
8. Вставь в Redmine

> Ревью не сбрасывается при открытии отчёта — можно продолжить добавлять комментарии.  
> Новое ревью начинается только при повторном нажатии **▶** (с подтверждением).

### Формат отчёта

```
{{Collapse(Ревью)
# @\console\controllers\PingController@ Issue #1
<pre><code class="php">
class PingController extends Controller
</code></pre>
# @\console\controllers\PingController@ Issue #2
<pre><code class="php">
public function actionIndex(){
    echo "Pong\r\n";
}
</code></pre>
# @\console\controllers\PingController::actionIndex@ Issue #3
}}
```

## Сборка и установка

### Требования
- JDK 21
- Gradle (wrapper включён)

### Сборка

```bash
./gradlew buildPlugin
```

Плагин появится в `build/distributions/code-review-plugin-1.0.1.zip`

### Установка в PhpStorm

1. **Settings → Plugins → ⚙️ → Install Plugin from Disk...**
2. Выбрать `build/distributions/code-review-plugin-1.0.1.zip`
3. Перезапустить PhpStorm

### Запуск в dev-режиме

```bash
./gradlew runIde
```

## Структура проекта

```
src/main/
├── kotlin/com/codereview/plugin/
│   ├── ReviewState.kt               # Состояние ревью, генерация отчёта
│   ├── ReviewCommentDialog.kt       # Диалог добавления комментария
│   ├── ReviewReportDialog.kt        # Диалог финального отчёта
│   ├── ReviewToolWindowFactory.kt   # Боковая панель
│   └── actions/
│       ├── StartReviewAction.kt     # Кнопка ▶ Старт
│       ├── AddCommentAction.kt      # Кнопка + / Ctrl+Alt+R
│       └── FinishReviewAction.kt    # Кнопка 📋 Отчёт
└── resources/META-INF/
    └── plugin.xml
```
