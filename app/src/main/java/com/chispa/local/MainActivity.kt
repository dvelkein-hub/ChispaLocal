package com.chispa.local

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Locale

class MainActivity : Activity() {
    companion object {
        private const val MODEL_NAME = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
        private const val MODEL_URL = "https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf?download=true"
        private const val PREFS = "chispa_model"
    }

    private lateinit var history: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var entry: EditText
    private lateinit var memory: LocalMemory
    private lateinit var modelButton: Button
    private lateinit var status: TextView
    private lateinit var analysisMode: CheckBox
    private val ui = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var pollDownload: Runnable? = null
    private var engine: InferenceEngine? = null
    private var modelReady = false
    private var working = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun modelFile() = File(getExternalFilesDir(null), MODEL_NAME)
    private fun prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun downloaded() = modelFile().isFile &&
        modelFile().length() > 900_000_000L && prefs().getBoolean("complete", false)

    private fun round(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(14).toFloat()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        memory = LocalMemory(this)
        window.statusBarColor = Color.rgb(8, 18, 35)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(13), dp(13), dp(8))
            setBackgroundColor(Color.rgb(8, 18, 35))
        }
        setContentView(root)
        fun header(text: String, size: Float): TextView = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.WHITE)
            setPadding(dp(5), dp(4), dp(5), dp(7))
        }
        root.addView(header("✦ CHISPA LOCAL  •  v0.3", 23f))
        status = header("Qwen 1.5B • Contexto 6144 • Ideas largas", 12f)
        status.setTextColor(Color.rgb(103, 221, 221))
        root.addView(status)
        modelButton = Button(this).apply { setOnClickListener { onModelButton() } }
        root.addView(modelButton)
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = "Mostrar flotante"
            setOnClickListener { startOverlay() }
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        buttons.addView(Button(this).apply {
            text = "Ocultar"
            setOnClickListener { stopService(Intent(this@MainActivity, OverlayService::class.java)) }
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(buttons)
        analysisMode = CheckBox(this).apply {
            text = "Análisis en 2 etapas (más lento, más detallado)"
            textSize = 13f
            setTextColor(Color.rgb(194, 229, 239))
            isChecked = prefs().getBoolean("analysis_mode", false)
            setOnCheckedChangeListener { _, enabled ->
                prefs().edit().putBoolean("analysis_mode", enabled).apply()
            }
        }
        root.addView(analysisMode)
        scroll = ScrollView(this)
        history = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(6))
        }
        scroll.addView(history)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        entry = EditText(this).apply {
            hint = "Habla con Chispa..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setSingleLine(false)
            minLines = 2
            maxLines = 7
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            gravity = Gravity.TOP
            imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION
        }
        bottom.addView(entry, LinearLayout.LayoutParams(0, -2, 1f))
        bottom.addView(Button(this).apply {
            text = "Enviar"
            setOnClickListener { send() }
        })
        root.addView(bottom)
        memory.messages().forEach { addBubble(it) }
        if (history.childCount == 0) {
            addBubble("Chispa: ¡Hola! Puedes descargar mi IA y hablar conmigo sin internet después.")
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 12)
        }
        refreshModelButton()
        if (!downloaded() && prefs().getLong("download_id", -1L) >= 0L) watchDownload()
    }

    private fun addBubble(message: String): TextView {
        val isUser = message.startsWith("Tú:")
        val container = LinearLayout(this).apply {
            gravity = if (isUser) Gravity.END else Gravity.START
            setPadding(0, dp(5), 0, dp(5))
        }
        val bubble = TextView(this).apply {
            text = message
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(dp(13), dp(10), dp(13), dp(10))
            background = round(if (isUser) Color.rgb(24, 91, 119) else Color.rgb(27, 44, 72))
        }
        container.addView(bubble, LinearLayout.LayoutParams(-2, -2).apply {
            val maxWidth = (resources.displayMetrics.widthPixels * 0.87).toInt()
            bubble.maxWidth = maxWidth
        })
        history.addView(container)
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        return bubble
    }

    private fun localCommand(msg: String): Boolean {
        val t = msg.trim().lowercase(Locale.ROOT).trim(' ', '?', '¿', '.')
        return t.startsWith("recuerda que ") || t == "mis notas" ||
            t == "qué recuerdas" || t == "que recuerdas" ||
            t.contains("batería") || t.contains("bateria") ||
            t == "hora" || t.contains("qué hora") || t == "fecha" ||
            Regex("^(-?\\d+(?:[.,]\\d+)?)\\s*([+*/x×-])\\s*(-?\\d+(?:[.,]\\d+)?)$").matches(t)
    }

    private fun send() {
        val msg = entry.text.toString().trim()
        if (msg.isEmpty()) return
        if (working) {
            Toast.makeText(this, "Espera a que Chispa termine su respuesta", Toast.LENGTH_SHORT).show()
            return
        }
        if (modelReady && msg.length > 9500) {
            AlertDialog.Builder(this).setTitle("Idea demasiado larga para esta versión")
                .setMessage("El texto supera el límite seguro de 9500 caracteres. Divídelo en varias partes; Chispa no debe recortar requisitos sin avisarte.")
                .setPositiveButton("Entendido", null).show()
            return
        }
        val deep = modelReady && (analysisMode.isChecked || msg.length >= 500)
        entry.setText("")
        val request = "Tú: " + msg
        addBubble(request)
        memory.addMessage(request)
        if (!modelReady || localCommand(msg)) {
            val reply = "Chispa: " + Rules.respond(this, memory, msg)
            addBubble(reply)
            memory.addMessage(reply)
            return
        }

        val answerBubble = addBubble(if (deep) "Chispa: Analizando objetivos y condiciones..." else "Chispa: Pensando...")
        working = true
        modelButton.isEnabled = false
        analysisMode.isEnabled = false
        val begin = SystemClock.elapsedRealtime()
        ui.launch {
            val output = StringBuilder()
            var updatedAt = 0L
            try {
                val ai = requireNotNull(engine) { "La IA no está disponible" }
                var notes = ""
                if (deep) {
                    status.text = "Paso 1/2 • Identificando restricciones..."
                    val plan = StringBuilder()
                    val analysisPrompt = """Analiza la solicitud siguiente. No respondas al usuario todavía.
                        |Identifica objetivos, datos, restricciones, tiempos y posibles contradicciones.
                        |No inventes información. Escribe notas breves y verificables.
                        |
                        |SOLICITUD:
                        |$msg""".trimMargin()
                    ai.sendUserPrompt(analysisPrompt, predictLength = 224).collect { part ->
                        plan.append(part)
                    }
                    notes = plan.toString().take(1600)
                    answerBubble.text = "Chispa: Preparando la respuesta..."
                }
                val finalPrompt = if (deep) {
                    """Responde ahora a la solicitud original, sin omitir ningún requisito.
                       |Revisa tus cálculos y condiciones. Si no estás seguro, dilo.
                       |Presenta una solución ordenada con explicaciones útiles.
                       |Apóyate en estas notas de análisis:
                       |$notes""".trimMargin()
                } else msg
                status.text = if (deep) "Paso 2/2 • Desarrollando idea..." else "Generando respuesta..."
                ai.sendUserPrompt(finalPrompt, predictLength = if (deep) 544 else 320).collect { chunk ->
                    output.append(chunk)
                    val now = SystemClock.elapsedRealtime()
                    if (now - updatedAt > 85L) {
                        answerBubble.text = "Chispa: " + output.toString()
                        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
                        updatedAt = now
                    }
                }
                val answer = output.toString().trim().ifEmpty { "No pude generar una respuesta." }
                answerBubble.text = "Chispa: " + answer
                memory.addMessage("Chispa: " + answer)
                status.text = "IA local • " + (if (deep) "Análisis" else "Normal") +
                    " • " + String.format(Locale.US, "%.1f s", (SystemClock.elapsedRealtime() - begin) / 1000.0) +
                    " • " + answer.length + " caracteres"
            } catch (e: Exception) {
                answerBubble.text = "Chispa: Ocurrió un error al generar: " + (e.message ?: "desconocido")
                status.text = "Error de generación. Intenta reiniciar el modelo."
            } finally {
                working = false
                analysisMode.isEnabled = true
                modelButton.isEnabled = true
            }
        }
    }

    private fun onModelButton() {
        if (modelReady || working) return
        if (downloaded()) {
            activateModel()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Descargar Qwen 1.5B")
                .setMessage("Se descargará una vez un archivo GGUF de aproximadamente 1 GB. La descarga requiere Wi-Fi sin medición; después la IA funcionará sin internet. El modelo es de Qwen (licencia Apache 2.0). ¿Continuar?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Descargar") { _, _ -> downloadModel() }
                .show()
        }
    }

    private fun refreshModelButton() {
        when {
            modelReady -> {
                modelButton.text = "IA Qwen 1.5B activa • Sin internet"
                modelButton.isEnabled = false
            }
            downloaded() -> {
                modelButton.text = "Activar IA Qwen 1.5B (offline)"
                modelButton.isEnabled = !working
            }
            else -> {
                modelButton.text = "Descargar IA Qwen 1.5B (~1 GB, Wi-Fi)"
                modelButton.isEnabled = true
            }
        }
    }

    private fun downloadModel() {
        try {
            val file = modelFile()
            if (file.exists()) file.delete()
            prefs().edit().putBoolean("complete", false).apply()
            val request = DownloadManager.Request(Uri.parse(MODEL_URL))
                .setTitle("Chispa • Qwen 1.5B")
                .setDescription("Descarga del modelo local GGUF")
                .setAllowedOverMetered(false)
                .setAllowedOverRoaming(false)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(this, null, MODEL_NAME)
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = dm.enqueue(request)
            prefs().edit().putLong("download_id", id).apply()
            status.text = "Descargando IA únicamente con Wi-Fi sin medición..."
            watchDownload()
        } catch (e: Exception) {
            status.text = "No pude iniciar la descarga: " + e.message
        }
    }

    private fun watchDownload() {
        pollDownload?.let { handler.removeCallbacks(it) }
        val r = object : Runnable {
            override fun run() {
                val id = prefs().getLong("download_id", -1L)
                if (id < 0L) return
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val query = DownloadManager.Query().setFilterById(id)
                dm.query(query)?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use
                    fun column(name: String) = cursor.getLong(cursor.getColumnIndexOrThrow(name))
                    val state = column(DownloadManager.COLUMN_STATUS).toInt()
                    val bytes = column(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val total = column(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    when (state) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            prefs().edit().putBoolean("complete", true)
                                .remove("download_id").apply()
                            status.text = "Modelo descargado • Pulsa Activar IA"
                            refreshModelButton()
                            return
                        }
                        DownloadManager.STATUS_FAILED -> {
                            prefs().edit().remove("download_id").apply()
                            status.text = "Falló la descarga. Toca Descargar IA para reintentar."
                            refreshModelButton()
                            return
                        }
                        else -> {
                            val pct = if (total > 0L) (bytes * 100L / total).toInt().toString() + "%" else "..."
                            modelButton.text = "Descargando modelo: " + pct
                            modelButton.isEnabled = false
                            status.text = "Descarga: " + (bytes / 1_000_000L) +
                                " MB • Esperando Wi-Fi si está pausada"
                        }
                    }
                }
                handler.postDelayed(this, 1800L)
            }
        }
        pollDownload = r
        handler.post(r)
    }

    private fun activateModel() {
        if (!downloaded()) {
            status.text = "Primero descarga completamente el archivo GGUF."
            return
        }
        working = true
        modelButton.isEnabled = false
        modelButton.text = "Cargando IA en memoria..."
        status.text = "Preparando el motor local: puede tardar la primera vez"
        ui.launch {
            try {
                val ai = AiChat.getInferenceEngine(applicationContext)
                engine = ai
                if (ai.state.value !is InferenceEngine.State.ModelReady) {
                    withTimeout(60000L) {
                        ai.state.first {
                            it is InferenceEngine.State.Initialized ||
                                it is InferenceEngine.State.ModelReady ||
                                it is InferenceEngine.State.Error
                        }
                    }
                    if (ai.state.value is InferenceEngine.State.Error) {
                        throw IllegalStateException("El motor nativo no se pudo iniciar")
                    }
                    if (ai.state.value is InferenceEngine.State.Initialized) {
                        ai.loadModel(modelFile().absolutePath)
                        ai.setSystemPrompt(
                            "Eres Chispa, un compañero digital original que vive en un teléfono Android. " +
                            "Responde en español, con naturalidad y claridad. " +
                            "No inventes permisos ni afirmes que ejecutaste acciones del sistema. " +
                            "Si no sabes algo, dilo. Responde de forma breve salvo que te pidan detalles."
                        )
                    }
                }
                modelReady = true
                status.text = "IA Qwen 1.5B lista • Respuestas 100% locales"
            } catch (e: Exception) {
                status.text = "No pude activar la IA: " + (e.message ?: "error")
                Toast.makeText(this@MainActivity,
                    "Error de activación. Consulta el estado en pantalla.", Toast.LENGTH_LONG).show()
            } finally {
                working = false
                refreshModelButton()
            }
        }
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Activa mostrar sobre otras apps y vuelve a tocar el botón", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
            return
        }
        try {
            val i = Intent(this, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo iniciar el flotante: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        pollDownload?.let { handler.removeCallbacks(it) }
        ui.cancel()
        super.onDestroy()
    }
}
