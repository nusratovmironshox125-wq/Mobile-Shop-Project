# Состояние Real Mobil Shop — 12 ноября 2026

Рабочий проект: C:\JAVA darsliklar\MODULE-9\Real_Mobil_Shop_Sayt. Ветка codex/real-mobil-shop. Основная реализация FIX-01…09 — 629738b; проверенная оптимизация COUNT/публичных характеристик — 03d99b7. Актуальные требования: MASTER_TZ 1.3 и корневой Real_Mobil_Shop_Codex_Fixes_SDLC.md. Размер страницы 10.

## Выполнено и сохранено

- Сохранены оба пользовательских файла application.properties и application-local.properties, они намеренно остаются изменёнными вне коммитов. Не reset/checkout/stash эти файлы. SHA256: основной 201e06d4d0af7484c75909556b50930996aae4244c3225a9a585376c0414b822, local 0de6f9ed6cbeb23e51649af49baf08f76c7cd08f2dac94fd34a17b63183508ea.
- JPA подтвердил real_mobil_shop_sayt/public/postgres/127.0.0.1:5432, PostgreSQL 18.4. Flyway V2 уже применена 11 ноября после свежего backup; Hibernate validation и fingerprints старых полей product/product_variant/lead PASS. База не пересоздавалась. Одна смешанная LAPTOP-комплектация сохранена с specs_review_required; её значения проверяет владелец перед явной очисткой.
- Реализованы глобальный счётчик/toast/независимое readAt, разные PHONE/LAPTOP, стабильная автоссылка, артикул/поиск, RU/UZ подписи, аудит username/role/до–после, серверные страницы по 10. COUNT упрощён без изменения условий фильтра; чужие legacy-поля скрыты публично, доступны в админке для подтверждённой очистки.
- Сохранены FIX_PLAN, FIX_ACCEPTANCE R-01…R-25, обновлённые OpenAPI/инструкция администратора/README/решения, production-профиль и nginx-шаблон, инструкции Telegram/backup/стенда и генератор нагрузки. Полные локальные нагрузочные измерения — docs/results/2026-11-12.

## Проверки

- final10: 17 JUnit PASS; восстановление приватного backup в отдельной БД, Flyway V2/Hibernate/fingerprints PASS. После bootstrap прогон прервался; его HTTP/E2E не считались пройденными.
- resume11: 381 HTTP PASS, короткий load smoke PASS. Новый browser-тест выявил неверный exact label селектор, исправлен тест. Код приложения ради него не менялся.
- final12: сборка frontend/JAR PASS; 17 JUnit, failures/errors/skipped 0; 381 HTTP; Edge 157.0.4341.42 PASS. Уведомление за 8363 мс, дедупликация вкладок, read не меняет NEW, offline/reconnect, новый лид на странице 3, категория/поля, цена/SKU/наличие/характеристики выбранного варианта, публикация/rename/стабильная ссылка, архивирование, история URL и пагинация. Размеры 320–1440 px; админ 360/390/430; RU/UZ/темы. Все процессы тестового запуска остановлены.
- Smoke: rms_load с 10000 моделей/20000 вариантов/40000 метаданных фото/100000 начальных заявок. 20 основных RPS, 2 с разогрева +10 с измерения, 10 администраторов; 221 HTTP, 22.1 фактических HTTP/с, ошибок/429/dropped 0. Две подтверждённые заявки сверены с БД. Это НЕ приёмка 1000/2000.
- OpenAPI YAML: 32 paths, 97 локальных ссылок разрешены; ссылки новых документов и git diff --check PASS.
- Рабочее приложение было запущено для проверки на 127.0.0.1:8080, PID 38376, local, Flyway false. После сообщения владельца о конфликте порта при запуске IntelliJ этот экземпляр идентифицирован по командной строке и остановлен; bind-проба подтвердила свободный порт 8080. Повторно фоновый сервер не запускать, запуском управляет владелец через IntelliJ. Проверка 12 ноября 11:05 местного времени: 5 моделей, ссылки 200, size=10, admin endpoints без входа 401, записей не создавалось. Проверочный сервер теперь остановлен. При продолжении сначала проверить порт; чужой процесс или экземпляр IntelliJ не останавливать. Логи приватные в %LOCALAPPDATA%/RealMobilShop/logs/local-2026-11-12*.log.

## Что остаётся и почему

- Реальный Telegram BLOCKED: нет настроенных TELEGRAM_BOT_TOKEN/TELEGRAM_CHAT_ID. Adapter/outbox проверены заглушкой и PostgreSQL; реальное сообщение и перезапуск с каналом не проверены. Секреты задаются владельцем локально, не в чат.
- 1000 посетителей/100 RPS ≥30 мин, 2000/200 RPS, 4 ч, скачок/холодный кеш/NAT/выше 2000 BLOCKED: нет отдельного разрешённого стенда и отдельного генератора. Нужны CPU/GC/DB waits/пул/очереди и сверка заявок. Цели ТЗ не снижены.
- Внешний HTTPS/CDN/WAF, origin IPv4/IPv6, несколько backend-экземпляров BLOCKED. Production-профиль/nginx — подготовленные шаблоны, не развёрнутая или проверенная внешняя защита; nginx -t на сервере ещё нужен.
- Реальные Android/iPhone, экранная клавиатура и slow-network/LCP/INP/CLS BLOCKED: устройств/стенда нет. Headless viewport не заменяет эти проверки.
- GitHub: https://github.com/nusratovmironshox125-wq/Mobile-Shop-Project.git подключён как origin по ссылке владельца. После подтверждения владельцем входа через Git Credential Manager ветка codex/real-mobil-shop опубликована; первый push 3c0b2a6 подтверждён совпадением локального HEAD и refs/heads/codex/real-mobil-shop на GitHub. Upstream настроен на origin/codex/real-mobil-shop. Перед отправкой проверены 11 коммитов/258 уникальных Git blobs: локальные пароли, токены и приватные ключи не обнаружены; найденные парольные значения CI — только тестовые placeholders. Два личных application*.properties остаются вне коммитов. GitHub CI — отдельная проверка, её успех не следует из локальных тестов.
- Ручной черновик/SELECT в IntelliJ по AT-26 выполняет владелец согласно его решению. Рабочие данные не использовались для синтетических записей/нагрузки.

## Точное продолжение

Доступная локальная реализация и проверки завершены; далее закрывать перечисленные внешние ограничения по FIX_ACCEPTANCE/FIX_OPERATIONS/scripts/load/README. Не повторять миграцию рабочей БД или завершённые тесты без новых изменений. Сначала git status/log, проверить сохранённые конфиги и текущий процесс на 8080. Продолжать в этом репозитории, не создавать новый.

Последний полный журнал: %TEMP%/rms-fixes-final12.log. Test root: %TEMP%/rms-test-07ab26cb6ee349da89948841eb7fe0d7. Load: %TEMP%/rms-load-smoke-4c272ae5656745e79fb84b1d4827dd02. Резервные копии: %LOCALAPPDATA%/RealMobilShop/backups/before-v2-1794401985687.dump (перед рабочей V2) и before-fixes-1794387780458.dump (проверенное восстановление). В Git эти файлы не добавлять.

Повторный полный тест при изменениях (PowerShell из корня, порты 55432/18080/18081 свободны):

    $env:JAVA_HOME = "C:\Users\user\.jdks\ms-21.0.12.1"
    $env:RMS_PLAYWRIGHT_DIR = Join-Path $env:TEMP "real-mobil-browser-qa"
    $env:RMS_BROWSER_EXE = "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-local.ps1 -LoadSmoke -BackupFile "C:\Users\user\AppData\Local\RealMobilShop\backups\before-fixes-1794387780458.dump"

-ReuseBuild пропускает сборку/JUnit и допустим только при неизменном проверенном JAR. Для локального сервера при свободном 8080: & "$env:JAVA_HOME\bin\java.exe" -jar target/Real_Mobil_Shop_Sayt-0.0.1-SNAPSHOT.jar --spring.profiles.active=local. Для внешнего выпуска собрать из чистого checkout: локальный JAR содержит личную конфигурацию и предназначен только для этого ПК.
