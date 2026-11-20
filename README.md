# VRG Android

**Модифицированный Telegram с запрещённым функционалом.**
*A modified Telegram client with forbidden features.*

Сайт / Website: https://vrgclient.app · Канал / Channel: [@VRGclient](https://t.me/VRGclient)

---

## Русский

VRG Android — неофициальный клиент на основе [Telegram для Android](https://github.com/DrKLO/Telegram), чёрно-белый, с набором возможностей, которых нет в оригинале:

- режим призрака: не отмечать прочитанным, не показывать «печатает» и «в сети», смотреть истории незаметно;
- удалённые сообщения остаются у вас, с историей правок;
- автоответчик и тихая отправка;
- **VRG Music**: свой плеер с офлайн-кешем, плейлистами, текстами песен, эквалайзером и радио;
- значки VRG рядом с именами, свой Premium-значок локально, фейк-профиль;
- пароли на чаты и аккаунты, мастер-пароль;
- до 99 аккаунтов, выделение больше 100 сообщений;
- свои темы, шрифт Inter и ещё много настроек в разделе «Настройки VRG».

**И это ещё не всё.**

### Скачать

Актуальная версия: **https://vrgclient.app**

### Сборка

Нужны Android Studio, Android NDK `27.2.12479018` и Android SDK 36, а также **свои** `api_id` и `api_hash` с https://my.telegram.org.

1. Склонируйте репозиторий.
2. В файл `local.properties` добавьте:
   ```
   VRG_API_ID=ваш_api_id
   VRG_API_HASH=ваш_api_hash
   ```
   Этот файл не попадает в репозиторий. Никогда не публикуйте свои ключи.
3. Соберите: `./gradlew :TMessagesProj_App:assembleAfatRelease`

Код VRG лежит в `TMessagesProj/src/main/java/org/telegram/vrg/`; остальное — небольшие вставки в оригинальные файлы.

## English

VRG Android is an unofficial client based on [Telegram for Android](https://github.com/DrKLO/Telegram), black and white, with extra features:

- ghost mode: no read receipts, no typing or online status, silent story viewing;
- deleted messages stay visible, with edit history;
- auto-responder and silent sending;
- **VRG Music**: built-in player with offline cache, playlists, lyrics, equalizer and radio;
- VRG badges next to names, a local Premium badge, fake profile;
- chat and account passwords, master password;
- up to 99 accounts, selecting more than 100 messages;
- custom themes, Inter font and more in "VRG settings".

**And that is not all.**

### Download

Latest version: **https://vrgclient.app**

### Building

You need Android Studio, Android NDK `27.2.12479018`, Android SDK 36 and **your own** `api_id` / `api_hash` from https://my.telegram.org.

1. Clone the repository.
2. Add to `local.properties`:
   ```
   VRG_API_ID=your_api_id
   VRG_API_HASH=your_api_hash
   ```
   This file is not tracked by git. Never publish your keys.
3. Build: `./gradlew :TMessagesProj_App:assembleAfatRelease`

VRG code lives in `TMessagesProj/src/main/java/org/telegram/vrg/`; everything else is small hooks into the original sources.

## License / Лицензия

VRG Android is based on Telegram for Android and is licensed under the GNU GPL v2, the same as the original. See `LICENSE`. Telegram names and logos belong to Telegram FZ-LLC. VRG is not affiliated with Telegram. Use at your own risk.

VRG Android основан на Telegram для Android и распространяется под лицензией GNU GPL v2, как и оригинал. См. `LICENSE`. Названия и логотипы Telegram принадлежат Telegram FZ-LLC. VRG не связан с Telegram. Используйте на свой риск.
