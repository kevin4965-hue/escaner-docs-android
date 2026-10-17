# Escáner Docs

App de Android para digitalizar documentos con la cámara del teléfono: escanea, ordena las páginas, exporta a PDF y extrae el texto (OCR). Todo funciona **en el propio teléfono**, sin servidores ni cuentas.

## Funciones (v0.2)

- **Escanear** con la cámara o importar fotos de la galería. Usa el escáner de documentos de Google (ML Kit), que hace:
  - detección automática de bordes y corrección de perspectiva
  - filtros (color, escala de grises, blanco y negro), limpieza de manchas y dedos, y rotación
- **Biblioteca** de documentos con miniaturas, renombrar y eliminar
- **Carpetas**: crear, renombrar y eliminar (los documentos de una carpeta eliminada pasan a la lista principal); mover documentos entre carpetas; escanear directamente dentro de una carpeta
- **Búsqueda** por nombre **y por el texto de las páginas** en todas las carpetas, con un fragmento del texto encontrado
- **Páginas**: añadir más, ver a pantalla completa, reordenar y eliminar
- **PDF buscable** en tamaño A4: el texto reconocido va como capa invisible sobre la imagen, así que se puede buscar, seleccionar y copiar en cualquier lector de PDF. Se puede compartir (WhatsApp, correo, Drive…) o guardar en *Descargas*
- **Extraer texto (OCR)** de todas las páginas sin conexión; copiar o compartir el texto
- **Firma**: dibuja tu firma una vez con el dedo (*⋮ → Mi firma*) y estámpala en cualquier página (abre la página → icono de firma); se arrastra a su lugar y se ajusta el tamaño con un gesto de pellizcar o con la barra

El OCR se hace una vez por página, en segundo plano después de escanear, y se guarda en caché para la búsqueda y el PDF.

## Tecnología

| Parte | Librería |
|---|---|
| Lenguaje e interfaz | Kotlin + Jetpack Compose (Material 3) |
| Escáner | Google ML Kit Document Scanner (vía Google Play Services) |
| OCR | Google ML Kit Text Recognition (alfabeto latino, en el dispositivo) |
| PDF con capa de texto | PdfBox-Android (Apache 2.0) |
| Imágenes | Coil |

Los documentos se guardan en el almacenamiento privado de la app (`files/documents/<id>/`): un `meta.json`, una imagen JPEG por página y un `*.ocr.json` por página con el texto reconocido y su posición. Las carpetas están en `files/documents/folders.json` y la firma en `files/signature.png`.

## Requisitos

- Android 8.0 (API 26) o superior, con Google Play Services
- Para compilar: Android Studio (JDK 17 o superior) y Android SDK 36

## Compilar e instalar

Abre la carpeta en Android Studio y pulsa **Run**, o desde la terminal:

```bash
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. Para instalarlo en un teléfono conectado por USB (con la depuración USB activada):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> **Windows con caracteres especiales (ñ, tildes) en la ruta de usuario:** si Gradle falla con `Unable to establish loopback connection`, define una carpeta temporal sin tildes antes de compilar, por ejemplo `set TEMP=C:\Users\Public\gradle-tmp` y `set TMP=C:\Users\Public\gradle-tmp`.

## Próximos pasos

- Marca de agua y contraseña en el PDF
- Etiquetas y subcarpetas
- Unir y dividir documentos; importar PDF existentes
