# TurnProxy Android Client

Android клиент для Turn Proxy — WebRTC VPN через TURN сервер.

## Функционал

- ✅ WebRTC DataChannel для туннелирования
- ✅ TURN сервер через coturn
- ✅ WebSocket signaling
- ✅ VPN Service для перехвата всего трафика
- ✅ Device token авторизация
- ✅ Статистика трафика
- ✅ Уведомления о статусе

## Требования

- Android Studio Hedgehog (2023.1.1) или новее
- Android SDK 34
- Gradle 8.0+
- Минимальная версия Android: 8.0 (API 26)

## Сборка

### 1. Клонирование

```bash
git clone <репозиторий>
cd turn-proxy-android
```

### 2. Открытие в Android Studio

Откройте проект в Android Studio, дождитесь загрузки Gradle.

### 3. Сборка APK

```bash
./gradlew assembleDebug
```

APK будет в `app/build/outputs/apk/debug/app-debug.apk`

### 4. Release сборка

```bash
./gradlew assembleRelease
```

APK будет в `app/build/outputs/apk/release/app-release.apk`

## Установка

### Через ADB

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Через Telegram

Отправьте APK файл в Telegram и установите на телефон.

## Настройка

### 1. Получение Device Token

1. Откройте Turn Proxy Panel: `http://194.76.172.67:3001`
2. Войдите под admin
3. Создайте пользователя
4. Создайте устройство для пользователя
5. Скопируйте device token

### 2. Настройка приложения

Откройте приложение TurnProxy VPN:

**Поля:**
- **Device Token:** Вставьте токен из панели
- **Server URL:** `ws://194.76.172.67:3001/ws`

### 3. Подключение

Нажмите "Подключить" и разрешите VPN подключение.

## Структура проекта

```
app/src/main/java/com/turnproxy/
├── ui/
│   └── MainActivity.kt          — Главный экран
├── vpn/
│   └── TurnProxyVpnService.kt   — VPN Service
├── webrtc/
│   └── WebRTCManager.kt        — WebRTC управление
└── signaling/
    └── SignalingManager.kt     — WebSocket signaling
```

## Протокол

### Аутентификация

```json
{
  "type": "auth",
  "token": "device_token"
}
```

### Ответ сервера

```json
{
  "type": "auth_ok",
  "session_id": "uuid"
}
```

### SDP Offer/Answer

```json
{
  "type": "offer",
  "sdp": "v=0..."
}
```

### ICE Candidates

```json
{
  "type": "candidate",
  "candidate": "candidate:...",
  "sdp_mid": "0",
  "sdp_mline_index": 0
}
```

## Конфигурация TURN

Сервер: `194.76.172.67`
Порт: `3478` (UDP/TCP)
Транспорт: UDP

## Логи

Для просмотра логов:

```bash
adb logcat | grep TurnProxy
```

Фильтры:
- `TurnProxyVpnService` — VPN логи
- `WebRTCManager` — WebRTC логи
- `SignalingManager` — WebSocket логи
- `MainActivity` — UI логи

## Устранение проблем

### Не подключается к WebSocket

1. Проверьте URL сервера
2. Убедитесь что порт 3001 открыт
3. Проверьте firewall

### WebRTC не устанавливается

1. Проверьте что TURN сервер доступен: `nc -zv 194.76.172.67 3478`
2. Проверьте логи coturn
3. Убедитесь что UDP порты 49152-65535 открыты

### VPN не перехватывает трафик

1. Разрешите VPN подключение в системных настройках
2. Проверьте логи: `adb logcat -s TurnProxyVpnService`
3. Убедитесь что DataChannel открыт

### Токен не принимается

1. Проверьте что токен верный
2. Убедитесь что устройство активно в панели
3. Проверьте логи Gateway

## Разработка

### Зависимости

- WebRTC: `org.webrtc:google-webrtc:1.0.32006`
- OkHttp: `com.squareup.okhttp3:okhttp:4.12.0`
- Gson: `com.google.code.gson:gson:2.10.1`
- Coroutines: `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3`

### Добавление функций

1. VPN туннелирование — уже реализовано
2. Split tunneling — можно добавить в `TurnProxyVpnService`
3. Auto-reconnect — добавить в `MainActivity`
4. Темы — добавить в `res/values/themes.xml`

## Безопасность

- ✅ Device token вместо паролей
- ✅ TLS для WebSocket (WSS) — можно включить
- ✅ TURN auth через HMAC-SHA256
- ✅ No hardcoded credentials

## Лицензия

MIT License

## Поддержка

Для проблем и вопросов:
- Логи: `adb logcat`
- Панель: `http://194.76.172.67:3001`
- 3X-UI: `http://194.76.172.67:2053`
