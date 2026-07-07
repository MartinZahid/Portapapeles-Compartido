# Clipboard Sync

Sincroniza el portapapeles del celular (Android) hacia una PC automáticamente.

## Arquitectura

```
Celular (Android) ──HTTP POST──> PC (servidor Go)
  ClipboardService         clipboard-sync.exe
  (AccessibilityService)   (escucha en :3737)
```

## Componentes

### Android app (`android/`)

App nativa en Kotlin que corre un `AccessibilityService` para leer el portapapeles y enviarlo al servidor.

**Permisos requeridos:**
- `BIND_ACCESSIBILITY_SERVICE` — para leer el portapapeles en foreground
- `SYSTEM_ALERT_WINDOW` — overlay invisible 1×1 que mantiene foco constante para leer el portapapeles en segundo plano en Samsung One UI
- `POST_NOTIFICATIONS` — notificación persistente indicando que el servicio está activo
- `INTERNET` — para enviar el texto al servidor

**Setup (2 pasos):**
1. Abrir Ajustes > Accesibilidad > Clipboard Sync y activar el toggle
2. Abrir Ajustes > Aplicaciones > Clipboard Sync > "Permitir superposición" (o tocar el botón en la app)

### Servidor (`main.go`)

Servidor HTTP mínimo en Go que recibe el texto y lo escribe al portapapeles de la PC.

```
Usage: clipboard-sync.exe [puerto]
  (default puerto: 3737)
```

**Endpoints:**
- `GET /clipboard` — devuelve el último texto recibido
- `POST /clipboard` — recibe texto (body plano `text/plain`)
- `GET /health` — health check

## Cómo funciona la lectura en segundo plano

Android 10+ restringe `ClipboardManager.primaryClip` solo a:
1. El IME por defecto
2. La app con una ventana con foco
3. La última app que escribió al portapapeles

Un `AccessibilityService` sin ventana visible no cumple ninguna condición. La solución es un **overlay invisible de 1×1 píxel con `TYPE_APPLICATION_OVERLAY` y sin `FLAG_NOT_FOCUSABLE`**, que mantiene una ventana con foco permanente. Así el sistema reconoce nuestra app como "app en primer plano" para el chequeo de acceso al portapapeles, incluso en Samsung One UI.

No requiere root, Shizuku, ni foreground service.
