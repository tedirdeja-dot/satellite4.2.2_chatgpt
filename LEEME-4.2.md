# Satellite Wallpaper 4.2.1

## Uso
1. Al abrir la app, elige 0, 15, 30 o 60 minutos. La elección queda guardada.
2. Opcionalmente pulsa Cambiar vista para navegar por Zoom Earth y guardar la zona.
   Este selector interactivo necesita Internet; no actualiza el fondo.
3. Bloquea y desbloquea el teléfono. La primera captura se inicia entonces.
4. Cuando termine, abre la app, ajusta el encuadre y acepta el fondo animado.
5. Las siguientes capturas solo se inician al recibir USER_PRESENT y si ha pasado
   estrictamente más del intervalo desde la última captura correcta. 0 significa
   cada desbloqueo. No hay temporizadores, trabajos periódicos ni reintentos diferidos.

Encender la pantalla sin desbloquear, abrir la app, volver al escritorio y reiniciar
no inician capturas. Al bloquear durante una captura se detiene la carga del WebView
y se descarta el resultado. Los avisos duplicados no generan descargas adicionales.
Un intento fallido conserva la imagen y la hora de la última actualización correcta.
La operación tarda unos segundos después del desbloqueo; usar el teléfono no dispara
otra operación. El sistema puede terminar una operación de fondo ya enviada a su API.

## Imagen temporal
Se guarda un único JPG vigente en cacheDir/satellite_cache.jpg, normalmente
/data/user/0/com.satellite.wallpaper/cache/satellite_cache.jpg. Es una carpeta privada,
no la galería. Android puede vaciarla si necesita espacio.
Durante la captura existe un JPG provisional; solo al completarlo correctamente se
sustituye el anterior mediante rename en el mismo directorio. Se limpian provisionales
de intentos interrumpidos. La copia persistente de 4.1 se elimina tras el primer éxito.
La fecha de actualización se dibuja sobre el fondo y sobre el fondo de bloqueo, como
en 4.1. El JPG fuente no lleva la fecha incrustada para que el recorte no la desplace.

## Memoria y rendimiento
No se utilizan condiciones por marca. La resolución mantiene la proporción y se limita
según memoria disponible: ancho máximo 720 px en equipos de memoria reducida, 1080 px
en los demás, con un presupuesto adicional de píxeles. Esto sacrifica algo de detalle
frente a la captura a resolución completa para reducir buffers y consumo de memoria.
Decodificación muestreada con RGB_565 y reintento a menor resolución si falta memoria.
JPEG calidad 90 (antes 100), compresión fuera del hilo principal, y reciclado de bitmaps.
Se conserva la espera de carga de teselas de 9 segundos: terminar de cargar el HTML no
garantiza que el mapa WebGL esté listo. No se inventa una API de descarga de Zoom Earth.

## Compilar en GitHub
Sube el CONTENIDO de esta carpeta a la raíz del repositorio, incluida .github.
El workflow mantiene Java 17, Gradle 8.10.2, AGP 8.7.3 y Kotlin 2.0.21 de tu configuración.
Genera gradlew si falta, ejecuta pruebas JVM y lint, compila y publica el APK como artifact.
Conserva applicationId y firma originales; versionCode sube de 20 a 21.
No se ha ejecutado este workflow desde esta sesión.

## Estado de verificación
Revisión estática de los puntos de entrada y XML realizada. Se incluyen pruebas JVM
de los cuatro intervalos, límites estrictos, primera imagen y retroceso del reloj.
NO ejecutadas aquí: compilación Android, lint, pruebas JVM, APK en emulador/dispositivo
y comparación real de velocidad. Este equipo carece del SDK/Java 17/Gradle y no resuelve
los servidores de descarga. No se garantiza ausencia de errores ni compatibilidad universal.
Requiere Android 6/API 23 o posterior, WebView funcional y soporte de fondos animados.
Los fabricantes pueden limitar servicios en segundo plano, y una app forzada a detenerse
debe abrirse de nuevo. La notificación de servicio debe permanecer activa.

## Verificación en dispositivos
- GitHub: comprobar que pruebas, lint y assembleDebug terminan correctamente.
- Probar 0/15/30/60 min, límite exacto, doble aviso, sin red, pantalla encendida bloqueada,
  uso prolongado, vuelta al escritorio, bloqueo durante captura, reinicio y cambio de zona.
- Probar un dispositivo de poca memoria y otro de mayor memoria, de marcas diferentes.
- Verificar que siempre se conserva el JPG anterior ante errores y solo hay un JPG vigente.
- Comparar 4.1 y 4.2 con la misma zona, móvil, red y caché fría/caliente por separado;
  medir al menos 10 desbloqueos por versión y comparar mediana y percentil 95 hasta
  ver imagen y fecha. En 4.2 el registro SatelliteTiming muestra capture_ms, jpeg_ms,
  bytes, tamaño y tiempo total. La anotación OK mide captura/cache, no el tiempo final
  de dibujo del launcher o de aplicación del fondo de bloqueo.
No se afirma un porcentaje de mejora: las optimizaciones aún requieren medición real.

Referencias: https://developer.android.com/reference/android/content/Intent
https://developer.android.com/reference/android/webkit/WebViewClient

## Corrección 4.2.1
El registro de GitHub aportado confirma que la versión 4.2 compiló Kotlin, ejecutó
testDebugUnitTest y generó el APK (assembleDebug). El workflow terminó con error por
UnspecifiedRegisterReceiverFlag en MainActivity, no por un error del compilador.
Se usan ContextCompat.registerReceiver y RECEIVER_NOT_EXPORTED en los tres puntos
de registro. Se añade AndroidX Core 1.13.1 y android.useAndroidX=true para compatibilidad
con versiones anteriores a Android 13. Lint sigue activado, sin baseline ni supresiones.
versionCode=22; versionName=4.2.1. El workflow no requiere cambios.
La corrección 4.2.1 se ha revisado estáticamente; debe volver a ejecutarse en GitHub.
La ejecución real de la app y las mediciones de velocidad siguen pendientes.
