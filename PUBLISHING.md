# Публикация Kosht в магазины приложений

## Общая подготовка (для обоих магазинов)

1. **Создай релизный ключ подписи** (один раз, храни как зеницу ока — потеряешь ключ, не сможешь обновлять приложение):
   ```powershell
   keytool -genkey -v -keystore kosht-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias kosht
   ```
   Файл `kosht-release.jks` НЕ коммить в git.

2. **Подключи ключ в `app/build.gradle.kts`** — замени `signingConfig = signingConfigs.getByName("debug")` на настоящий конфиг:
   ```kotlin
   signingConfigs {
       create("release") {
           storeFile = file("../kosht-release.jks")
           storePassword = System.getenv("KOSHT_STORE_PASSWORD")
           keyAlias = "kosht"
           keyPassword = System.getenv("KOSHT_KEY_PASSWORD")
       }
   }
   ```
   и в `buildTypes.release`: `signingConfig = signingConfigs.getByName("release")`.

3. **Собери AAB** (магазины принимают App Bundle, не APK):
   ```powershell
   .\gradlew.bat :app:bundleRelease
   ```
   Файл: `app/build/outputs/bundle/release/app-release.aab`.

4. **Подними versionCode** в `app/build.gradle.kts` перед каждой новой загрузкой (2, 3, 4…).

5. **Подготовь материалы** (уже почти всё есть):
   - Иконка 512×512 PNG (экспортируй адаптивную иконку из Android Studio: Image Asset)
   - Скриншоты телефона — папка `screenshots/` уже готова
   - Feature graphic 1024×500 (баннер для Play)
   - Краткое (до 80 символов) и полное (до 4000) описание — можно взять из README
   - **Privacy Policy URL** — обязательна для обоих магазинов. Приложение не собирает данные, так что политика на одну страницу; можно захостить бесплатно на GitHub Pages (файл `privacy.html` в репозитории → Settings → Pages).

## Google Play

1. Зарегистрируй аккаунт разработчика: https://play.google.com/console — единоразовый взнос **$25** (нужна карта).
2. **Важно для личных аккаунтов:** Google требует закрытое тестирование с минимум 12 тестировщиками в течение 14 дней, прежде чем откроет доступ к продакшену. Планируй это заранее (позови друзей/родных).
3. Console → «Создать приложение» → заполни анкеты: контент-рейтинг, целевая аудитория, безопасность данных («данные не собираются» — честно для Kosht).
4. Загрузка: Release → Testing → Closed testing → создай трек, загрузи `app-release.aab`.
5. После 14 дней тестирования подай заявку на продакшен. Проверка обычно 1–7 дней.
6. Обновления: поднимаешь `versionCode`, собираешь новый AAB, загружаешь в консоль — пользователи получат обновление автоматически.

## Huawei AppGallery

1. Зарегистрируйся: https://developer.huawei.com → AppGallery Connect. Для физлица регистрация **бесплатна**, нужна верификация паспортом (1–2 дня).
2. AppGallery Connect → «Мои приложения» → «Новое приложение» (Android).
3. Загрузи тот же `app-release.aab` (или APK — AppGallery принимает оба), заполни описание, скриншоты, рейтинг.
4. Privacy Policy URL обязательна, как и в Play.
5. Проверка обычно 1–3 дня. Требований к предварительному тестированию, как у Google, нет — публикация проще.
6. Замечание: на новых Huawei без Google-сервисов приложение работает полностью — Kosht не использует GMS.

## Автосборка (уже настроена)

`.github/workflows/release.yml` собирает APK на каждый пуш в `master` и публикует его в GitHub Releases:

1. Создай репозиторий на GitHub и запушь код:
   ```powershell
   git remote add origin https://github.com/<твой-логин>/kosht.git
   git push -u origin master
   ```
2. Всё. После каждого пуша через ~5 минут в разделе Releases появится готовый `app-release.apk` — качай на телефон и обновляйся. В магазины загружать по-прежнему нужно вручную (Google и Huawei не позволяют иначе без платных сервисов типа Fastlane + API-ключей — можно добавить позже).
