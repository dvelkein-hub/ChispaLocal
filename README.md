# Chispa Local — v0.2 (Android)

Prototipo original de asistente flotante con chat **local** basado en **Qwen2.5-1.5B-Instruct Q4_K_M** (modelo descargable) y biblioteca Android de **llama.cpp**.

## Qué incluye

- Personaje flotante y arrastrable, chat por burbujas, notas SQLite, comandos instantáneos (hora, batería y aritmética).
- Motor nativo de llama.cpp integrado con Gradle y CMake para arm64-v8a; modelo GGUF de aproximadamente 0,99 GB descargable con confirmación, usando Wi-Fi sin medición.
- La descarga es necesaria una sola vez; posteriormente la conversación se ejecuta en el teléfono sin usar servidores externos.
- El modelo se activa manualmente: no permanece procesando mientras no lo uses.
- Contexto inicial de 2048 tokens y límite de generación de 160 tokens por respuesta. Optimización/velocidad real pendientes de medir en Helio G85.
- No incorpora todavía ejecución autónoma de acciones Android ni alarmas programadas.

## Compilación sin Android Studio

Abrir **Actions > Compilar Chispa Android**, seleccionar la última compilación verde y descargar el artefacto **ChispaLocal-v0.2-Qwen-APK**. Extraer el ZIP e instalar `app-debug.apk` en Android 13 o superior ARM64.

**Nota sobre actualizaciones:** las APK de depuración pueden firmarse con claves diferentes en distintos ejecutores de GitHub. Si Android rechaza actualizar la v0.1, necesitarás desinstalar esa versión antes de instalar la nueva; **la desinstalación puede borrar las notas locales**, así que respalda cualquier texto que necesites.

## Prueba del motor

1. Abrir la app; confirmar que sigue funcionando el personaje flotante.
2. Conectar el teléfono a Wi-Fi sin medición.
3. Pulsar **Descargar IA Qwen 1.5B** y confirmar el tamaño (~1 GB).
4. Tras finalizar, pulsar **Activar IA** y esperar la carga a memoria.
5. Probar conversación abierta y comparar tiempos de respuesta; consultar batería y cálculos seguirá usando funciones deterministas.

## Dependencias y atribución

- Biblioteca oficial [llama.cpp](https://github.com/ggml-org/llama.cpp), licencia MIT. Se recupera un commit exacto en el flujo de compilación y se ajusta su contexto por configuración de compilación.
- [Qwen2.5-1.5B-Instruct](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct), y [versión GGUF Q4_K_M de bartowski](https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF), licencia Apache 2.0.
- El personaje de Chispa y su interfaz son originales; no se emplean recursos de Mega Man.
