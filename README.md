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
| MEDIA_DIR | Постоянный каталог файлов вне JAR |
| PRIVACY_RU, PRIVACY_UZ, PRIVACY_VERSION, RETENTION_DAYS | Утверждённое информирование и период хранения; 180 дней по умолчанию |
| PHONE_REGIONS | Разрешённые страны libphonenumber, по умолчанию UZ |
| TELEGRAM_BOT_TOKEN, TELEGRAM_CHAT_ID | Токен и подтверждённый получатель, оба необязательны |

Spring читает окружение процесса. Шаблон `.env.example` перечисляет имена, он не загружается автоматически.

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

- [ТЗ 1.1](docs/MASTER_TZ.md), [решения](docs/DECISIONS.md), [архитектура и ERD](docs/ARCHITECTURE.md), [OpenAPI](docs/openapi.yaml).
- [Управление магазином](docs/ADMIN_GUIDE.md), [выпуск](docs/DEPLOYMENT.md), [эксплуатация](docs/RUNBOOK.md).
- Точный URL GitHub пока не подтверждён. Инвентаризация и условия подключения записаны в [STATUS](docs/STATUS.md).
