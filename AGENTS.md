# AGENTS.md — инструкции для агентов

PhpStorm-плагин «Code Review Helper»: code review в IDE с экспортом отчёта в формат Redmine (Textile).
Kotlin, Gradle (Kotlin DSL), `org.jetbrains.intellij.platform` 2.12.0, целевая IDE — PhpStorm 2025.1+.

## Команды

```bash
./gradlew buildPlugin       # сборка ZIP в build/distributions/ (+ автогенерация updatePlugins.xml)
./gradlew verifyPlugin      # проверка совместимости верификатором (против PhpStorm 2025.1)
./gradlew runIde            # запуск песочницы IDE с плагином
```

Для запуска задач сборки с генерацией XML нужен параметр URL (любое значение, используется в updatePlugins.xml):

```bash
./gradlew buildPlugin -PpluginDownloadUrl=https://example.com/x.zip
```

Требования: JDK 21, Gradle через wrapper.

## Версионирование и релизы

- **Единственный источник версии — файл `.app_version`** (формат `X.Y.Z`). `build.gradle.kts` читает его,
  пропатчивает в `plugin.xml` и использует в `updatePlugins.xml`. Нигде больше версию руками не менять.
- Диапазон IDE (`since-build`/`until-build`) задаётся в `val pluginSinceBuild` / `pluginUntilBuild`
  в `build.gradle.kts` и применяется и к `plugin.xml`, и к `updatePlugins.xml` — править только там.
- Релиз = `echo "X.Y.Z" > .app_version` → commit `Версия X.Y.Z` → tag `vX.Y.Z` → push.
  **Тег обязан совпадать с `.app_version`** — CI проверяет и падает при несовпадении.
- CI (`.github/workflows/release.yml`) по тегу `v*`: сборка + `verifyPlugin` → GitHub Release
  с ZIP и `updatePlugins.xml` (+ автогенерация описания `generate_release_notes`).
- `updatePlugins.xml` генерируется задачей `generateUpdatePluginsXml` (висит на `buildPlugin` через
  `finalizedBy`), в нём только **последняя** версия плагина (один `id` — одна запись).
- Не двигать уже опубликованные теги и не править `updatePlugins.xml` руками — он генерируется.
- Описание релиза можно править в UI GitHub после публикации — это не трогает тег и файлы.

## Репозиторий плагинов (пользователи)

- Основной URL (постоянный, всегда последний релиз):
  `https://github.com/egrekov/code-review-plugin/releases/latest/download/updatePlugins.xml`
- Запасной (GitHub Pages, у части провайдеров `github.io` заблокирован):
  `https://egrekov.github.io/code-review-plugin/updatePlugins.xml`

## Инварианты и подводные камни

1. **`gradle.properties` не должен содержать `org.gradle.java.home`** — машинные пути ломают CI
   (раннеры GitHub ставят JDK в другое место; путь `/usr/lib/jvm/...` существовал только на старом образе ubuntu-latest).
2. **`gradle-wrapper.jar` должен быть официальным** — CI проверяет SHA-256 через wrapper-validation.
   Если нужно обновить wrapper: `./gradlew :wrapper --gradle-version <X>`, не копировать jar из других проектов.
3. **`buildSearchableOptions`/`prepareJarSearchableOptions` отключены** (запускают полноценную IDE и падают
   на нестандартных путях) — не включать обратно без необходимости.
4. `plugin id` (`com.codereview.plugin`) — в `src/main/resources/META-INF/plugin.xml`; в Kotlin-коде id не дублируется,
   в `build.gradle.kts` он парсится из plugin.xml. Имя в updatePlugins.xml (`<name>`) должно совпадать с именем в plugin.xml.
5. Атрибут `url` в updatePlugins.xml — абсолютный HTTPS-ссылка на ZIP; IDE следует редиректам (GitHub Releases
   отдаёт файлы через `release-assets.githubusercontent.com`).
6. Скрытые jar-зависимости не добавлять в `lib/` — зависимости объявлять в `dependencies { implementation(...) }`.

## Стиль

- Коммиты и документация — на русском; сообщения коммитов краткие, императивные; релизный коммит — `Версия X.Y.Z`.
- Комментарии в коде объясняют «почему», не «что» (пример: отключение searchable options в `build.gradle.kts`).
- README.md — документация для пользователей (установка, использование, релизы); этот файл — для агентов.
