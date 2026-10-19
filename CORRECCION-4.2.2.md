# Corrección 4.2.2
La captura aportada muestra el servicio activo, pero ningún intento de captura.
No prueba un fallo de renderizado de Zoom Earth: todavía no hay una imagen en caché.

Se corrige la recepción de USER_PRESENT y SCREEN_OFF usando RECEIVER_EXPORTED
solo en los receptores de estas acciones protegidas del sistema. Sus handlers
ignoran cualquier otra acción. CACHE_UPDATED y STATUS siguen siendo NOT_EXPORTED.
La documentación de Android advierte que NOT_EXPORTED no recibe todos los avisos
de componentes privilegiados. Esto es una causa posible, no confirmada en el móvil.
https://developer.android.com/develop/background-work/background-tasks/broadcasts

Se registran los motivos de omisión: bloqueo activo, intervalo no elegido, descarga
en curso o intervalo aún no transcurrido. El estado y los errores se muestran en vivo.
Se recupera la imagen de filesDir de versiones antiguas hacia cacheDir si existe.
No se añade descarga al abrir la app, al encender la pantalla ni durante el uso.
Se mantiene la sustitución del JPG temporal solo tras captura válida.

Prueba: instala encima, abre la app, selecciona 0 minutos para comprobar la recepción,
bloquea y desbloquea completamente. Mantén el móvil desbloqueado mientras captura.
El registro debe mostrar aviso recibido, iniciando captura y OK o el error concreto.
Después puedes seleccionar el intervalo habitual.

Revisión estática y ZIP comprobados; esta versión no ha sido compilada ni ejecutada
en el dispositivo desde este entorno. No se afirma que el problema esté resuelto
en el teléfono hasta completar esa prueba. No hay medición nueva de velocidad.
