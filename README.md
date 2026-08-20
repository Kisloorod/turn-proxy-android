# TurnProxy Android VPN

Android клиент для Turn Proxy — WebRTC VPN через TURN сервер.

![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/Kisloorod/turn-proxy-android/build-apk.yml)
![GitHub Release](https://img.shields.io/github/v/release/Kisloorod/turn-proxy-android)
![Android](https://img.shields.io/badge/Android-8.0%2B-green)

## 🚀 Быстрая установка

### Скачать APK

Самый простой способ — скачать готовый APK из Releases:

1. Перейди в [Releases](https://github.com/Kisloorod/turn-proxy-android/releases)
2. Скачай `app-debug.apk` или `app-release.apk`
3. Установи на телефон

### Или собрать из исходников

```bash
git clone https://github.com/Kisloorod/turn-proxy-android.git
cd turn-proxy-android
./gradlew assembleDebug
```

APK будет в `app/build/outputs/apk/debug/app-debug.apk`

## 📱 Функционал

- ✅ WebRTC DataChannel для туннелирования
- ✅ TURN сервер (coturn)
- ✅ WebSocket signaling
- ✅ VPN Service для перехвата всего трафика
- ✅ Device token авторизация
- ✅ Статистика трафика (⬇ вниз / ⬆ вверх)
- ✅ Уведомления о статусе

## 🔧 Настройка

### 1. Получение Device Token

1. Открой панель: `http://194.76.172.67:3001`
2. Логин: `admin` / `admin123`
3. Пользователи → Создать пользователя
4. Устройства → Создать устройство
5. Скопируй **Device Token**

### 2. Настройка приложения

Открой **TurnProxy VPN** на телефоне:

**Поля:**
- **Device Token:** Вставь токен из панели
- **Server URL:** `ws://194.76.172.67:3001/ws`

### 3. Подключение

1. Нажми **"Подключить"**
2. Разреши VPN подключение
3. Жди "Подключено"

## 🛠 Разработка

### Требования

- Android Studio Hedgehog (2023.1.1) или новее
- Android SDK 34
- Gradle 8.0+
- Минимум Android 8.0 (API 26)

### Сборка

```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease

# Установка через ADB
adb install app/build/outputs/apk/debug/app-debug.apk
```

### GitHub Actions

Автосборка APK работает через GitHub Actions:

- **push в main/master** — собирает debug + release
- **тег v*** — создаёт Release с APK
- **workflow_dispatch** — ручной запуск

Смотри статус в [Actions](https://github.com/Kisloorod/turn-proxy-android/actions).

## 📊 Структура проекта

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

## 🔒 Безопасность

- ✅ Device token вместо паролей
- ✅ Временные TURN креды (HMAC-SHA256)
- ✅ TLS для WebSocket (WSS) — можно включить
- ✅ No hardcoded credentials

## 🐛 Устранение проблем

### Не подключается к WebSocket

1. Проверь URL сервера
2. Убедись что порт 3001 открыт
3. Проверь firewall

**Логи:**
```bash
adb logcat | grep SignalingManager
```

### WebRTC не устанавливается

1. Проверь TURN сервер: `nc -zv 194.76.172.67 3478`
2. Проверь что UDP порты 49152-65535 открыты
3. Проверь логи coturn

**Логи:**
```bash
adb logcat | grep WebRTCManager
```

### VPN не перехватывает трафик

1. Разреши VPN подключение
2. Проверь логи: `adb logcat -s TurnProxyVpnService`
3. Убедись что DataChannel открыт

### Токен не принимается

1. Проверь что токен верный
2. Убедись что устройство активно в панели
3. Проверь логи Gateway

## 📝 Логи

```bash
# Все логи приложения
adb logcat | grep TurnProxy

# Специфичные модули
adb logcat -s TurnProxyVpnService    # VPN
adb logcat -s WebRTCManager         # WebRTC
adb logcat -s SignalingManager      # WebSocket
adb logcat -s MainActivity           # UI
```

## 📡 Протокол

### Аутентификация

```json
{
  "type": "auth",
  "token": "eyJ1c2VyX2lkIjoi..."
}
```

### Ответ сервера

```json
{
  "type": "auth_ok",
  "session_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

### SDP Offer/Answer

```json
{
  "type": "offer",
  "sdp": "v=0\r\no=- 1234567890..."
}
```

### ICE Candidates

```json
{
  "type": "candidate",
  "candidate": "candidate:1 1 UDP 2130706431 192.168.1.1 54433 typ host",
  "sdp_mid": "0",
  "sdp_mline_index": 0
}
```

## 🚀 Конфигурация

### Server URL

По умолчанию: `ws://194.76.172.67:3001/ws`

Для WSS (безопасный): `wss://194.76.172.67:3001/ws`

### TURN Сервер

- Хост: `194.76.172.67`
- Порт: `3478` (UDP/TCP)
- Транспорт: UDP
- Relay порты: `49152-65535`

## 📋 TODO

- [ ] WSS (WebSocket Secure)
- [ ] Auto-reconnect
- [ ] Split tunneling
- [ ] Темная тема
- [ ] Выбор TURN сервера
- [ ] История подключений
- [ ] QR код для токена

## 📄 Лицензия

MIT License

## 💡 Советы

1. **Первое подключение** может занять 10-20 секунд (ICE gathering)
2. **Статус бар** показывает подключение VPN
3. **Трафик** обновляется каждую секунду
4. **Уведомление** — можно свернуть приложение

## 🆘 Поддержка

- Панель: `http://194.76.172.67:3001`
- 3X-UI: `http://194.76.172.67:2053`
- Логи сервера: `docker compose logs gateway`
- Логи TURN: `docker compose logs coturn`

---

**Размер APK:** ~5 MB (debug), ~3 MB (release)
**Минимальная версия:** Android 8.0 (API 26)
**Целевая версия:** Android 14 (API 34)
