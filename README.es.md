# NetAnalyzer

![Android](https://img.shields.io/badge/Android-3DDC84?logo=android&logoColor=white)
![Java](https://img.shields.io/badge/Java-ED8B00?logo=openjdk&logoColor=white)
![API](https://img.shields.io/badge/API-26%2B-brightgreen?)
![Android Studio](https://img.shields.io/badge/Android%20Studio-1976D2?logo=androidstudio&logoColor=fff)
![Gradle](https://img.shields.io/badge/Gradle-02303A?logo=gradle&logoColor=white)

[🇬🇧 English](README.md) · [🇪🇸 Spanish](README.es.md)

**NetAnalyzer** es una aplicación Android nativa para la auditoría de redes locales sin necesidad de privilegios de superusuario (root). Permite descubrir dispositivos activos, identificar sus características principales (IP, MAC, fabricante, sistema operativo estimado, hostname) y detectar servicios expuestos mediante el escaneo de puertos, todo ello desde un dispositivo móvil.

## ¿Qué problema resuelve?

Las herramientas de auditoría de red tradicionales (Nmap, Wireshark, Nessus) requieren un equipo de escritorio y, en muchos casos, privilegios elevados. En Android, las aplicaciones existentes suelen presentar limitaciones importantes: algunas exigen root, otras ofrecen funcionalidades reducidas o dependen de servicios externos.

NetAnalyzer cubre ese hueco proporcionando una herramienta **portable, ligera y funcional sin root**, capaz de realizar análisis preliminares de seguridad directamente desde un smartphone. Está orientada a administradores de sistemas, técnicos de soporte, estudiantes de redes y usuarios avanzados que necesiten inspeccionar su red doméstica o corporativa.

## ¿Cómo funciona?

El funcionamiento se articula en torno a un flujo de descubrimiento, análisis y enriquecimiento:

1. **Descubrimiento de dispositivos.** Se ejecutan simultáneamente varios mecanismos activos y pasivos:
   - **ARP**: lectura de la tabla ARP del sistema (solo en versiones donde está permitido).
   - **ICMP**: ping nativo para detectar hosts accesibles.
   - **TCP**: conexiones a puertos comunes para inferir actividad.
   - **mDNS/DNS-SD**: consultas multicast para resolver nombres `.local` y servicios anunciados.
   - **SSDP (UPnP)**: envío de `M-SEARCH` para obtener descripciones XML de dispositivos IoT.
   - **NetBIOS**: consultas NBSTAT para entornos Windows.

2. **Fusión y correlación de información.** Los resultados de todos los métodos se consolidan en una colección thread-safe (`ListDeviceInfo`) que utiliza la dirección IP como clave primaria. Cada campo (sistema operativo, hostname, modelo, fabricante) se encapsula en un `PriorityValue` con un nivel de confianza, de modo que la información más fiable prevalece sobre la menos fiable, independientemente del orden de llegada de los datos.

3. **Fingerprinting.** Se infiere información adicional a partir de:
   - El **TTL** de las respuestas ICMP/TCP (estimación del sistema operativo).
   - Los **prefijos OUI** de las direcciones MAC (fabricante).
   - Los **banners** de servicios (SSH, HTTP, etc.).

4. **Enriquecimiento con IA (opcional).** Durante el descubrimiento SSDP, las cabeceras y los XML de descripción se envían a un modelo de lenguaje alojado en OpenRouter.ai, que devuelve un JSON estructurado con fabricante, modelo, sistema operativo, número de serie y servicios. Este módulo se configura con una clave API propia del usuario.

5. **Escaneo de puertos.** Una vez identificados los hosts activos, se lanza un escaneo TCP concurrente con Java NIO sobre un conjunto configurable de puertos (top 100, 500 o 1000).

6. **Presentación reactiva.** Todo el estado del escaneo (fase activa, progreso, dispositivos descubiertos, información de red) se centraliza en un `ScanRepository` y se propaga a la interfaz mediante `ViewModel` y `LiveData`.

## Características principales

- **Detección múltiple de dispositivos**: ARP, ICMP, TCP, mDNS, SSDP y NetBIOS, ejecutados en paralelo.
- **Fusión inteligente de datos**: evita duplicados y resuelve contradicciones mediante prioridades.
- **Fingerprinting**: estimación de sistema operativo por TTL, fabricante por MAC y banner grabbing.
- **Enriquecimiento con IA**: parseo semántico de dispositivos UPnP/SSDP vía OpenRouter (opcional).
- **Escaneo de puertos concurrente** con NIO y cancelación cooperativa.
- **Historial**: hasta 20 escaneos anteriores persistidos localmente.
- **Configuración avanzada**: selección de métodos, nivel de puertos y parámetros específicos (pings, timeouts, hilos, etc.).
- **Interfaz moderna**: temas claro/oscuro, tres idiomas (español, euskera, inglés) y animaciones.
- **Servicio en primer plano**: el escaneo continúa en segundo plano con notificación y opción de cancelación.

## Arquitectura y tecnologías

La aplicación sigue una arquitectura modular inspirada en **Clean Architecture** y el patrón **MVVM**:

- **Capa de presentación**: `Single-Activity` con `Fragments`, `RecyclerView` y Material Design.
- **Capa de estado**: `ScanViewModel` + `LiveData` + `ScanRepository` como fuente única de verdad.
- **Capa de servicio**: `ScanService` (ForegroundService) orquesta el escaneo y la cancelación.
- **Núcleo de red**: `NetworkScanner` coordina las implementaciones de `DiscoveryMethod` y el escaneo de puertos.
- **Modelos de datos**: `NetworkInfo`, `DeviceInfo`, `ScanState`.

**Tecnologías principales**:
- Java + Android SDK (API 26+)
- Java NIO (`SocketChannel` + `Selector`)
- `NsdManager` y `MulticastSocket` para mDNS/SSDP
- `OkHttp3` para llamadas HTTP (macvendors.com y OpenRouter)
- `Gson` y `SharedPreferences` para persistencia ligera

## Requisitos y permisos

- **Android 8.0 (API 26)** o superior.
- Permisos: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `ACCESS_FINE_LOCATION` (para obtener SSID/BSSID).
- Android 13+: `NEARBY_WIFI_DEVICES` y `POST_NOTIFICATIONS`.
- **No requiere root** en ninguna versión.

## Uso

1. Abre la aplicación: se muestra la información de la red actual (IP, máscara, gateway, DNS).
2. Pulsa **SCAN** para iniciar el análisis. La interfaz muestra el progreso en tiempo real y los dispositivos detectados a medida que aparecen.
3. Accede a la pestaña **Dispositivos** para consultar la lista completa y ver el detalle de cada uno (puertos abiertos, servicios, metadatos).
4. En **Ajustes** puedes cambiar el tema, el idioma, seleccionar los métodos de descubrimiento, configurar el nivel de puertos y establecer tu clave API de OpenRouter.

## Limitaciones importantes

- **Acceso a la tabla ARP**: bloqueado a partir de Android 10 sin root. El método ARP solo funciona en versiones anteriores o en dispositivos rooteados; en el resto, el descubrimiento depende de ICMP, TCP y sondeos multicast.
- **Captura pasiva de tráfico**: no es posible sin root. Solo puede inspeccionarse el tráfico generado por el propio dispositivo mediante `VpnService`.
- **Escaneo UDP**: limitado por la naturaleza sin conexión del protocolo y la falta de control sobre mensajes ICMP de error; en la práctica se restringe a puertos conocidos (DNS, NTP, SNMP, SSDP).
- **Ping ICMP real y escaneo SYN**: requieren sockets raw y, por tanto, root.
- **Aleatorización de MAC**: en Android 10+ la MAC puede no corresponder al fabricante real del hardware, afectando a la identificación por OUI.

Estas limitaciones son consecuencia del modelo de seguridad de Android y condicionan el alcance del análisis, pero la combinación de múltiples mecanismos permite obtener una visión útil y fiable de la red en la mayoría de escenarios.
