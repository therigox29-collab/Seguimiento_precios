# Mis Precios (Windows)

Versión de escritorio de la app de seguimiento de precios, hecha con Kotlin +
Compose Multiplatform. Comparte la misma lógica de scraping que la app Android,
pero guarda todo en una base SQLite local en tu computadora (no en la nube) y
revisa precios cada 1 minuto por defecto (configurable en Ajustes), ya que en
escritorio no hay restricciones de batería como en el celular.

## Cómo conseguir el .exe

Igual que con la app Android, este entorno no tiene acceso a los servidores
necesarios para compilar, así que preparé el repo para que **GitHub lo compile
solo en una máquina Windows real**, gratis:

1. Creá un repositorio nuevo en GitHub (o reutilizá uno)
2. Subí **todo el contenido de esta carpeta**, incluida la carpeta oculta `.github`
   (usá "uploading an existing file" y arrastrá todo, o creá manualmente
   `.github/workflows/build-windows.yml` con "Add file > Create new file" si tu
   explorador te sigue ocultando esa carpeta)
3. Andá a la pestaña **Actions** del repo y esperá a que corra "Build Windows App"
   (tarda un poco más que el de Android, puede llevar 8-12 min)
4. Cuando termine con tilde verde ✅, bajá el artifact **MisPrecios-Windows-portable**
   — es una carpeta lista para usar, no necesita instalación: descomprimí el zip y
   ejecutá `MisPrecios.exe` que está adentro
5. Si además aparece **MisPrecios-Windows-installer**, es un instalador .exe más
   tradicional (con acceso directo en el menú Inicio) — usalo si preferís instalar
   la app en vez de tenerla en una carpeta suelta. Puede que ese paso falle en el
   primer intento por un tema de herramientas del runner; si eso pasa, no importa,
   la versión portable de todas formas funciona perfecto.

Windows probablemente te muestre una advertencia de "Windows protegió su PC"
al abrir el .exe la primera vez, porque no está firmado digitalmente (eso cuesta
dinero y no tiene sentido para una app personal). Tocá "Más información" →
"Ejecutar de todas formas".

## Diferencias con la versión Android

- Revisa cada 1 minuto por defecto (configurable de 1 a 60 min) — en el celular
  el mínimo recomendado era 5 min por batería, acá no aplica esa limitación.
- Las notificaciones aparecen como globo desde el ícono de la bandeja del sistema
  (system tray), no como notificación push de Android.
- Los datos (productos, historial de precios) se guardan en
  `%APPDATA%\MisPrecios\misprecios.db` — es un archivo independiente del que usa
  la app del celular, no se sincronizan entre sí automáticamente.
