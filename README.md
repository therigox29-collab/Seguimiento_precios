# Mis Precios

App Android (Kotlin + Jetpack Compose) para seguir precios de productos por URL y avisarte
cuando cambian.

## ⚠️ Importante: cómo conseguir el APK

Este proyecto **no viene con un .apk ya compilado** porque el entorno donde lo armé no tiene
acceso a los servidores de Google (dl.google.com / maven.google.com) que hacen falta para
compilar una app Android. Por eso preparé el repo para que **GitHub lo compile solo**, gratis,
sin que instales nada en tu computadora. Seguí estos pasos:

### 1. Crear cuenta de GitHub (si no tenés)
Andá a https://github.com/signup y creá una cuenta gratuita.

### 2. Crear un repositorio nuevo
- Entrá a https://github.com/new
- Ponele un nombre, por ejemplo `mis-precios`
- Dejalo en **Public** o **Private** (cualquiera funciona)
- No marques "Add a README" (ya tenemos uno)
- Tocá **Create repository**

### 3. Subir estos archivos
En la página del repo recién creado vas a ver un link que dice **"uploading an existing file"**.
Tocalo, y arrastrá **todo el contenido** de la carpeta descomprimida (incluida la carpeta oculta
`.github`, muy importante, es la que arma el APK automáticamente). Confirmá el commit con
**Commit changes**.

> Si no ves la carpeta `.github` al descomprimir, activá "mostrar archivos ocultos" en tu
> explorador de archivos — en algunos sistemas las carpetas que empiezan con punto están
> ocultas por defecto.

### 4. Esperar a que se compile
- Andá a la pestaña **Actions** de tu repositorio
- Vas a ver un workflow llamado **Build APK** corriendo (círculo amarillo). Tarda entre 3 y 6
  minutos.
- Cuando termine (tilde verde ✅), tocalo, y abajo de todo vas a ver un archivo para descargar
  llamado **MisPrecios-debug-apk** (es un .zip que contiene el .apk adentro).

### 5. Instalar en tu teléfono
- Descargá ese .zip en tu celular (o pasalo desde la PC por cable, Drive, WhatsApp, etc.)
- Descomprimilo, vas a tener `app-debug.apk`
- Tocalo para instalar. Android te va a pedir permiso para "instalar apps de orígenes
  desconocidos" la primera vez — es normal, aceptalo solo para este archivo.

Cada vez que quieras una versión actualizada (si le pedís cambios), solo hace falta subir los
archivos nuevos al mismo repo y Actions genera un APK nuevo solo.

### Alternativa: Android Studio
Si en algún momento preferís compilarlo vos desde una computadora, también funciona como
proyecto normal de Android Studio: `File > Open`, elegís esta carpeta, esperás que sincronice
Gradle (ahí sí con tu propia conexión a internet) y `Build > Build Bundle(s)/APK(s) > Build APK(s)`.

## Qué hace la app

- **Inicio**: lista de productos seguidos con precio actual y variación.
- **Agregar producto**: pegás una URL, la app detecta nombre/precio/imagen (usa JSON-LD,
  Open Graph, microdata o un patrón de moneda como respaldo — funciona en la mayoría de
  tiendas online, pero algunos sitios con protección anti-bot pueden fallar).
- **Detalle**: precio actual, gráfico de historial y lista de cambios.
- **Ajustes**: elegís entre "modo rápido" (foreground service con notificación fija,
  configurable de 1 a 14 min, pensado para pruebas cortas) o "modo ahorro" (WorkManager en
  segundo plano, de 15 a 120 min, mucho más eficiente en batería — recomendado para uso diario).
- **Notificaciones**: se disparan cuando el precio de un producto cambia, con opción de avisar
  solo en bajadas.

## Estructura del proyecto

```
app/src/main/java/com/rodrigo/misprecios/
  data/           Room (base de datos local) + DataStore (ajustes)
  network/        Scraper de precios (OkHttp + Jsoup)
  notifications/  Canales y notificaciones
  work/           WorkManager (modo ahorro)
  service/        Foreground Service (modo rápido)
  ui/             Pantallas en Jetpack Compose + navegación
```

## Antes de publicar en Play Store

- Cuenta de developer: 25 USD, pago único.
- Cuentas personales nuevas necesitan pasar 12 testers usando la app 14 días seguidos
  (closed testing) antes de habilitar producción.
- Para monetizar con anuncios hay que integrar el SDK de AdMob (no incluido todavía en este
  proyecto) y, si tenés usuarios en Europa/UK, sumar el SDK de consentimiento (Google UMP).
- El foreground service ya está declarado con tipo `dataSync` en el manifest, tal como pide
  Android 14+, pero conviene revisar la política de Play Console sobre foreground services
  antes de publicar.
