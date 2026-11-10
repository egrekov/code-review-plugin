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

## Установка через репозиторий плагинов

**Settings → Plugins → ⚙️ → Manage Plugin Repositories → +**, добавить:

```
https://github.com/egrekov/code-review-plugin/releases/latest/download/updatePlugins.xml
```

После этого плагин **Code Review Helper** появится во вкладке Marketplace, а новые версии будут предлагаться при проверке обновлений (при старте IDE или **Help → Check for Updates**).

URL постоянный — `latest` всегда указывает на последний релиз.

## Сборка и установка из файла

### Требования
- JDK 21
- Gradle (wrapper включён)

### Сборка

```bash
./gradlew buildPlugin
```

Плагин появится в `build/distributions/code-review-plugin-{version}.zip`
Рядом с ним генерируется `updatePlugins.xml` для кастомного репозитория (см. `generateUpdatePluginsXml` в `build.gradle.kts`).

### Установка из файла в PhpStorm

1. **Settings → Plugins → ⚙️ → Install Plugin from Disk...**
2. Выбрать `build/distributions/code-review-plugin-{version}.zip`
3. Перезапустить PhpStorm

### Запуск в dev-режиме

```bash
./gradlew runIde
```

## Выпуск новой версии

```bash
echo "1.0.9" > .app_version
git add -A && git commit -m "Версия 1.0.9"
git tag v1.0.9 && git push origin master --tags
```

Дальше всё делает CI ([.github/workflows/release.yml](.github/workflows/release.yml)) по тегу `v*`:

1. Проверяет, что тег совпадает с версией в `.app_version` (иначе прогон падает).
2. Собирает плагин и прогоняет `verifyPlugin` — проверку совместимости с PhpStorm (верификатор работает против PhpStorm 2025.1).
3. Публикует GitHub Release с ZIP-архивом и `updatePlugins.xml`.
4. Дублирует `updatePlugins.xml` на GitHub Pages (для сетей, где `github.io` доступен — у некоторых провайдеров он заблокирован).

Ссылки на репозиторий плагинов после релиза:

| URL | Заметка |
|-----|---------|
| `https://github.com/egrekov/code-review-plugin/releases/latest/download/updatePlugins.xml` | основной, URL не меняется от релиза к релизу |
| `https://egrekov.github.io/code-review-plugin/updatePlugins.xml` | запасной (GitHub Pages) |

## Структура проекта

```
src/main/
├── kotlin/com/codereview/plugin/
│   ├── ReviewState.kt               # Состояние ревью, генерация отчёта
│   ├── ReviewSession.kt             # Модели: ReviewComment, ReviewSession
│   ├── ReviewSessionManager.kt      # Хранение и переключение сессий ревью
│   ├── SessionManagerDialog.kt      # Диалог управления сессиями
│   ├── CommentEditorPanel.kt        # Панель редактирования (комментарий, Selected code)
│   ├── EditCommentDialog.kt         # Диалог редактирования существующего комментария
│   ├── ReviewCommentDialog.kt       # Диалог добавления комментария
│   ├── ReviewReportDialog.kt        # Диалог финального отчёта
│   ├── ReviewToolWindowFactory.kt   # Боковая панель
│   └── actions/
│       ├── StartReviewAction.kt     # Кнопка ▶ Старт
│       ├── AddCommentAction.kt      # Кнопка + / Ctrl+Alt+R
│       ├── FinishReviewAction.kt    # Кнопка 📋 Отчёт
│       └── SessionsAction.kt        # Кнопка списка сохранённых сессий
└── resources/META-INF/
    └── plugin.xml
```
