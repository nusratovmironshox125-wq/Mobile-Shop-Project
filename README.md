# Real Mobil Shop

Адаптивная витрина телефонов и ноутбуков с заявками. Spring Boot 4.1.2, JDK 21, Spring Security, Spring Data JPA/Hibernate, PostgreSQL 18, Flyway, React 19, TypeScript и Vite. Клиентский интерфейс и API обслуживает один JAR.

## Запуск в Windows

Требуются JDK 21, Node.js 22.12+ и PostgreSQL 18 с базой в кодировке UTF-8. Откройте PowerShell. Укажите путь к установленному JDK 21, если в PATH есть другая версия.

```powershell
Set-Location -LiteralPath 'C:\JAVA darsliklar\MODULE-9\Real_Mobil_Shop_Sayt'
$env:JAVA_HOME = 'C:\путь\к\jdk-21'
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build.ps1
```

Для первого запуска используйте пустую локальную базу, например `real_mobil_shop_dev`, и отдельную роль с правом создать схему. При подключении существующей `real_mobil_shop_sayt` сначала выполните инвентаризацию, резервное копирование и сверку миграций по [инструкции выпуска](docs/DEPLOYMENT.md). Flyway применяет миграции при запуске. Демо-товары и пользователи не создаются миграциями.

```powershell
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:5432/real_mobil_shop_dev'
$env:DB_USER = 'имя_локальной_роли'
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Пароль БД' -AsSecureString)).Password
$env:APP_SECURE_COOKIE = 'false' # Только для локального HTTP
$env:SHOP_ORIGIN = 'http://localhost:8080'
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
$jar = 'target/Real_Mobil_Shop_Sayt-0.0.1-SNAPSHOT.jar'
$env:OWNER_LOGIN = 'shop-owner'
$env:OWNER_NAME = 'Владелец'
$env:OWNER_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Пароль владельца от 12 символов' -AsSecureString)).Password
& $java -jar $jar bootstrap-owner
if ($LASTEXITCODE) { throw 'Не удалось создать владельца' }
Remove-Item Env:OWNER_PASSWORD
& $java -jar $jar
```

Откройте [витрину](http://localhost:8080/) или [вход для сотрудников](http://localhost:8080/admin/login). Команда владельца выполняется один раз. Пароль следует убрать из окружения после команды. Команда `recover-owner` на сервере сбрасывает пароль существующего OWNER по `OWNER_LOGIN`, отзывает его сессии и пишет событие аудита.

| Переменная | Назначение |
| --- | --- |
| DB_URL, DB_USER, DB_PASSWORD | JDBC URL и права runtime. Имя БД по умолчанию real_mobil_shop_sayt |
| DB_MIGRATION_USER, DB_MIGRATION_PASSWORD | Отдельная роль Flyway при развёртывании |
| APP_BIND, APP_PORT, APP_SECURE_COOKIE, SHOP_ORIGIN | Адрес приложения, локальный порт, Secure cookie, публичный origin |
| LOCAL_FLYWAY_ENABLED | Явное включение Flyway в профиле local после проверки схемы |
| MEDIA_DIR | Постоянный каталог файлов вне JAR |
| PRIVACY_RU, PRIVACY_UZ, PRIVACY_VERSION, RETENTION_DAYS | Утверждённое информирование и период хранения; 180 дней по умолчанию |
| PHONE_REGIONS | Разрешённые страны libphonenumber, по умолчанию UZ |
| TELEGRAM_BOT_TOKEN, TELEGRAM_CHAT_ID | Токен и подтверждённый получатель, оба необязательны |

Spring читает окружение процесса. Шаблон `.env.example` перечисляет имена, он не загружается автоматически.

## Проверка рабочей базы владельцем (AT-26)

В IntelliJ выберите профиль `local` и задайте `DB_PASSWORD` в локальной Run Configuration. Для PowerShell используйте окружение текущего процесса. Профиль задаёт `jdbc:postgresql://localhost:5432/real_mobil_shop_sayt?currentSchema=public` и пользователя `postgres`.

```powershell
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Пароль PostgreSQL' -AsSecureString)).Password
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
$jar = 'target/Real_Mobil_Shop_Sayt-0.0.1-SNAPSHOT.jar'
& $java -jar $jar inspect-database
```

Команда запускает JPA с профилями `local,db-inspect`, отключает Flyway и DDL и печатает метаданные соединения и список таблиц. Ожидайте `AT-26 DataSource: MATCH`, базу `real_mobil_shop_sayt`, схему `public`, пользователя `postgres` и порт `5432`. Для диагностики из той же Run Configuration укажите `inspect-database` как единственный Program argument. Если параметры отличаются, проверьте прямые `SPRING_DATASOURCE_*`, параметры JVM и аргументы запуска. Удалите Program argument после проверки.

Перед включением миграций сверьте существующую схему с Flyway и подготовьте резервную копию согласно [инструкции выпуска](docs/DEPLOYMENT.md). По умолчанию в `local` Flyway выключен, а Hibernate проверяет схему. Включить Flyway после согласования миграции можно через `LOCAL_FLYWAY_ENABLED=true`. Если в подготовленной базе ещё нет OWNER, выполните bootstrap из инструкции выше с `SPRING_PROFILES_ACTIVE=local` в окружении. Для обычного запуска подготовленной схемы выполните `& $java -jar $jar --spring.profiles.active=local`.

В подготовленной схеме откройте панель и создайте один черновик с уникальным slug, например `at26-test-20261108`. В окне Database IntelliJ выберите соединение с этой базой и выполните:

```sql
SELECT current_database(), current_schema(), current_user;
SELECT id, slug, status FROM public.product WHERE slug = 'at26-test-20261108';
```

Ожидается одна запись со статусом `DRAFT`. Затем архивируйте черновик через панель и повторите SELECT. Ожидаемый статус `ARCHIVED`. Если в панели уже есть такой slug, выберите другой и подставьте его в запрос. Для итоговой записи AT-26 достаточно профиля, результата диагностики и ID черновика. Пароль в отчёт не включайте.
## Проверка

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-local.ps1
```

Скрипт поднимает отдельный PostgreSQL 18 на 55432, приложение на 18080, создаёт синтетический каталог, выполняет JUnit и HTTP-проверки и останавливает оба процесса. Скрипт принимает `-PostgresBin`, если PostgreSQL установлен в другой папке. Протокол проверки и состояние приведены в [ACCEPTANCE](docs/ACCEPTANCE.md) и [STATUS](docs/STATUS.md).

Чтобы включить проверку в Edge через Playwright, перед запуском скрипта выполните:

```powershell
$env:RMS_PLAYWRIGHT_DIR = Join-Path $env:TEMP 'real-mobil-browser-qa'
npm.cmd install --prefix $env:RMS_PLAYWRIGHT_DIR playwright
$env:RMS_BROWSER_EXE = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
```

Временные тестовые журналы остаются в каталоге, который выводит скрипт.

## Исходный код и эксплуатация

- [ТЗ 1.2](docs/MASTER_TZ.md), [решения](docs/DECISIONS.md), [архитектура и ERD](docs/ARCHITECTURE.md), [OpenAPI](docs/openapi.yaml).
- [Управление магазином](docs/ADMIN_GUIDE.md), [выпуск](docs/DEPLOYMENT.md), [эксплуатация](docs/RUNBOOK.md).
- Точный URL GitHub пока не подтверждён. Инвентаризация и условия подключения записаны в [STATUS](docs/STATUS.md).
