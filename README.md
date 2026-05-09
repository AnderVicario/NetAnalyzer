NetAnalyzer
===========

**NetAnalyzer** es una app Android para analizar redes locales: detecta dispositivos activos, obtiene IP, MAC, fabricante, sistema operativo estimado (por TTL), servicios disponibles (mDNS, SSDP, NetBIOS) y escanea puertos comunes.

⚡ Características
-----------------

*   **Detección múltiple**: ARP, ICMP, TCP, mDNS, SSDP, NetBIOS
    
*   **Escaneo de puertos** asíncrono con NIO (concurrente)
    
*   **Fusión inteligente** de datos para evitar duplicados
    
*   **Historial** de los últimos 20 escaneos
    
*   **Configuración avanzada**: elegir métodos, nivel de puertos (100/500/1000), parámetros TCP/ICMP/SSDP
    
*   **Interfaz moderna**: temas claro/oscuro, 3 idiomas (es, eu, en), animaciones
    
*   **Servicio en primer plano** con notificación y cancelación
    
*   **OpenRouter opcional** para enriquecer respuestas SSDP (modelo, fabricante, OS, puertos)
    

🛠️ Tecnologías
---------------

*   Java, Android SDK (API 23+)
    
*   NIO (SocketChannel + Selector)
    
*   NsdManager (mDNS), MulticastSocket (SSDP/mDNS)
    
*   OpenRouter API (opcional, parseo de descripciones)
    
*   Gson, Material Design Components
    

📦 Requisitos
-------------

*   Permisos: INTERNET, ACCESS\_NETWORK\_STATE, ACCESS\_WIFI\_STATE, ACCESS\_FINE\_LOCATION (opcional para WiFi)
    
*   ARP requiere root en Android 10+ (se desactiva automáticamente si no hay acceso a /proc/net/arp)
    

🔧 Configuración
----------------

1.  **Clave OpenRouter** (opcional): en Ajustes → Clave API, pégala para mejorar SSDP.
    
2.  **Archivos de puertos**: la app espera top100.txt, top500.txt, top1000.txt en assets/. Si no existen, usa una lista por defecto.
    

🚀 Uso
------

1.  Abre la app → información de red actual.
    
2.  Pulsa **SCAN** → animación y progreso en tiempo real.
    
3.  Durante el escaneo, ve a **Dispositivos** para ver resultados parciales.
    
4.  Al terminar, el resumen se guarda en el historial (pantalla principal).
    
5.  **Ajustes**: cambia tema, idioma, métodos, nivel de puertos, etc.
