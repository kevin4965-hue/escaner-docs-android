# Escáner Docs

App de Android para digitalizar documentos con la cámara del teléfono: escanea, ordena las páginas, exporta a PDF y extrae el texto (OCR). Todo funciona **en el propio teléfono**, sin servidores ni cuentas.

## Funciones (v0.1)

- **Escanear** con la cámara o importar fotos de la galería. Usa el escáner de documentos de Google (ML Kit), que hace:
  - detección automática de bordes y corrección de perspectiva
  - filtros (color, escala de grises, blanco y negro), limpieza de manchas y dedos, y rotación
- **Biblioteca** de documentos con miniaturas, búsqueda por nombre, renombrar y eliminar
- **Páginas**: añadir más, ver a pantalla completa, reordenar y eliminar
- **PDF** en tamaño A4: compartir (WhatsApp, correo, Drive…) o guardar en *Descargas*
- **Extraer texto (OCR)** de todas las páginas sin conexión; copiar o compartir el texto

## Tecnología

| Parte | Librería |
|---|---|
| Lenguaje e interfaz | Kotlin + Jetpack Compose (Material 3) |
| Escáner | Google ML Kit Document Scanner (vía Google Play Services) |
| OCR | Google ML Kit Text Recognition (alfabeto latino, en el dispositivo) |
| PDF | `android.graphics.pdf.PdfDocument` (incluido en Android) |
| Imágenes | Coil |

Los documentos se guardan en el almacenamiento privado de la app (`files/documents/<id>/`), con un `meta.json` y una imagen JPEG por página.

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

- PDF con texto buscable (capa OCR invisible)
- Carpetas y etiquetas; búsqueda dentro del texto reconocido
- Firma, marca de agua y contraseña en el PDF
- Unir y dividir documentos; importar PDF existentes
