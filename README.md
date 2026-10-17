# Chispa Local — v0.3 (Android)

Prototipo original de asistente flotante con chat **local** basado en **Qwen2.5-1.5B-Instruct Q4_K_M** (modelo descargable) y biblioteca Android de **llama.cpp**.

## Qué incluye

- Personaje flotante y arrastrable, chat por burbujas, notas SQLite, comandos instantáneos (hora, batería y aritmética).
- Motor nativo de llama.cpp integrado con Gradle y CMake para arm64-v8a; modelo GGUF de aproximadamente 0,99 GB descargable con confirmación, usando Wi-Fi sin medición.
- La descarga es necesaria una sola vez; posteriormente la conversación se ejecuta en el teléfono sin usar servidores externos.
- El modelo se activa manualmente: no permanece procesando mientras no lo uses.
- Contexto experimental de 6144 tokens y límites de generación de 320 tokens (normal) o 544 tokens (análisis). Modo análisis de dos etapas con extracción de restricciones y respuesta final. La calidad sigue limitada por el modelo de 1.5B; más contexto no equivale a mayor inteligencia.
- No incorpora todavía ejecución autónoma de acciones Android ni alarmas programadas.

## Compilación sin Android Studio

Abrir **Actions > Compilar Chispa Android**, seleccionar la última compilación verde y descargar el artefacto **ChispaLocal-v0.3-Qwen-APK**. Extraer el ZIP e instalar `app-debug.apk` en Android 13 o superior ARM64.

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


## Prueba de ideas largas (v0.3)
- Entrada multilínea de hasta **9500 caracteres** por mensaje mientras está activada la IA. Si el mensaje es más extenso, se pide dividirlo en partes; no se recorta silenciosamente.
- **Análisis en 2 etapas:** casilla opcional. Se activa automáticamente si el texto tiene 500 o más caracteres. Primera etapa extrae requisitos; segunda elabora la respuesta. Esta modalidad tarda más y puede gastar más batería.
- El contexto aumentado a 6144 tokens también se comparte con los mensajes anteriores. **No significa memoria ilimitada** y se pueden perder detalles en conversaciones muy largas.
- Cálculos, calendarios y verificaciones complejas todavía necesitan herramientas deterministas que se añadirán después.
- La actualización del APK de GitHub Actions podría requerir reinstalación, debido a la firma de depuración. La desinstalación puede borrar tanto conversaciones como el archivo GGUF de ~1GB. No desinstales la versión actual si no estás dispuesto a volver a descargar el modelo.
- **Mediciones pendientes:** velocidad real, consumo de RAM, latencia de la primera respuesta, estabilidad con análisis de 2 etapas.
