package com.chispa.local

import android.Manifest
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.FrameLayout
import android.content.res.ColorStateList
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
import android.content.ActivityNotFoundException
import kotlinx.coroutines.CancellationException
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
    private lateinit var stopButton: Button
    private lateinit var sendButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var modelState: TextView
    private lateinit var composeCount: TextView
    private var generationJob: Job? = null
    private val BACKUP_REQUEST = 601
    private val RESTORE_REQUEST = 602
    private val EXPORT_MEMORY_REQUEST = 603
    private val IMPORT_MEMORY_REQUEST = 604
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
        window.statusBarColor = ChispaUi.BACKGROUND
        window.navigationBarColor = ChispaUi.BACKGROUND
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ChispaUi.BACKGROUND)
        }
        setContentView(root)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(17), dp(14), dp(11))
        }
        root.addView(header)
        val avatar = ChispaUi.avatar(this).apply {
            contentDescription = "Personaje de Chispa"
        }
        header.addView(avatar, LinearLayout.LayoutParams(dp(48), dp(48)))
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        titles.addView(ChispaUi.label(this, "Chispa", 23f, ChispaUi.TEXT, true))
        titles.addView(ChispaUi.label(this, "Asistente local  •  v0.5", 12f, ChispaUi.MUTED))
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        val menuButton = ChispaUi.button(this, "☰").apply {
            textSize = 19f
            contentDescription = "Abrir menú y herramientas"
            setOnClickListener { showToolsMenu() }
        }
        header.addView(menuButton, LinearLayout.LayoutParams(dp(50), dp(47)))

        val modelCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(12))
            background = ChispaUi.background(this@MainActivity, ChispaUi.SURFACE, 18, true)
        }
        root.addView(modelCard, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(14), dp(3), dp(14), dp(11))
        })
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(ChispaUi.label(this, "MOTOR LOCAL  ·  QWEN 1.5B", 11f, ChispaUi.MUTED, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        modelState = ChispaUi.label(this, "SIN ACTIVAR", 10f, ChispaUi.ACCENT, true)
        top.addView(modelState)
        modelCard.addView(top)
        status = ChispaUi.label(this, "Comprobando modelo instalado...", 12f, ChispaUi.MUTED)
        modelCard.addView(status, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(7)
        })
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            progress = 0
            visibility = View.GONE
            progressTintList = ColorStateList.valueOf(ChispaUi.ACCENT)
            progressBackgroundTintList = ColorStateList.valueOf(ChispaUi.OUTLINE)
        }
        modelCard.addView(progressBar, LinearLayout.LayoutParams(-1, dp(5)).apply {
            topMargin = dp(9)
        })
        modelButton = ChispaUi.button(this, "Preparando...", primary = true).apply {
            setOnClickListener { onModelButton() }
        }
        modelCard.addView(modelButton, LinearLayout.LayoutParams(-1, dp(45)).apply {
            topMargin = dp(11)
        })

        val conversationTitle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(19), dp(3), dp(19), dp(8))
        }
        conversationTitle.addView(ChispaUi.label(this, "CONVERSACIÓN", 11f, ChispaUi.MUTED, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        conversationTitle.addView(ChispaUi.label(this, "Privada · en el teléfono", 11f, ChispaUi.MUTED))
        root.addView(conversationTitle)

        scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(13), dp(4), dp(13), dp(9))
        }
        history = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(14))
        }
        scroll.addView(history)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(9), dp(13), dp(12))
            background = ChispaUi.background(this@MainActivity, ChispaUi.SURFACE, 19)
        }
        root.addView(composer, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(8), 0, dp(8), dp(7))
        })
        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        analysisMode = CheckBox(this).apply {
            text = "Analizar con más detalle"
            textSize = 12f
            setTextColor(ChispaUi.MUTED)
            buttonTintList = ColorStateList.valueOf(ChispaUi.ACCENT)
            isChecked = prefs().getBoolean("analysis_mode", false)
            setOnCheckedChangeListener { _, enabled ->
                prefs().edit().putBoolean("analysis_mode", enabled).apply()
            }
            minHeight = dp(38)
        }
        modeRow.addView(analysisMode, LinearLayout.LayoutParams(0, dp(38), 1f))
        composeCount = ChispaUi.label(this, "0 / 9500", 11f, ChispaUi.MUTED)
        modeRow.addView(composeCount)
        composer.addView(modeRow)

        val composeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        entry = EditText(this).apply {
            hint = "Escribe tu mensaje o una idea larga..."
            textSize = 15f
            setTextColor(ChispaUi.TEXT)
            setHintTextColor(ChispaUi.MUTED)
            setSingleLine(false)
            minLines = 2
            maxLines = 6
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            gravity = Gravity.TOP or Gravity.START
            imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION
            background = ChispaUi.background(this@MainActivity, ChispaUi.BACKGROUND, 13, true)
            setPadding(dp(13), dp(11), dp(13), dp(11))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val n = s?.length ?: 0
                    composeCount.text = "$n / 9500"
                    composeCount.setTextColor(if (n > 9500) Color.rgb(248, 144, 145) else ChispaUi.MUTED)
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        composeRow.addView(entry, LinearLayout.LayoutParams(0, -2, 1f))
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, 0, 0)
        }
        sendButton = ChispaUi.button(this, "➤", true).apply {
            textSize = 19f
            contentDescription = "Enviar mensaje"
            setOnClickListener { send() }
        }
        actions.addView(sendButton, LinearLayout.LayoutParams(dp(56), dp(49)))
        stopButton = ChispaUi.button(this, "■").apply {
            textSize = 16f
            contentDescription = "Detener generación"
            visibility = View.GONE
            setOnClickListener { generationJob?.cancel() }
        }
        actions.addView(stopButton, LinearLayout.LayoutParams(dp(56), dp(44)).apply {
            topMargin = dp(4)
        })
        composeRow.addView(actions)
        composer.addView(composeRow)

        val previous = memory.messages()
        if (previous.isEmpty()) showWelcome()
        else previous.forEach { addBubble(it) }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 12)
        }
        refreshModelButton()
        if (!downloaded() && prefs().getLong("download_id", -1L) >= 0L) watchDownload()
    }

    private fun setGenerationUi(active: Boolean) {
        sendButton.isEnabled = !active
        stopButton.visibility = if (active) View.VISIBLE else View.GONE
        analysisMode.isEnabled = !active
    }

    private fun showWelcome() {
        val welcome = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(24), dp(18), dp(22))
            background = ChispaUi.background(this@MainActivity, ChispaUi.SURFACE, 19, true)
        }
        val portrait = ChispaUi.avatar(this)
        welcome.addView(portrait, LinearLayout.LayoutParams(dp(68), dp(68)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        welcome.addView(ChispaUi.label(this, "¡Hola, estoy aquí!", 20f, ChispaUi.TEXT, true).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        welcome.addView(ChispaUi.label(this,
            "Soy Chispa, tu compañero local. Puedes conversar, guardar notas o pedir un recordatorio.",
            13f, ChispaUi.MUTED).apply {
            gravity = Gravity.CENTER
            setLineSpacing(dp(3).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        val examples = listOf("¡Hola!" to "hola",
            "Mis notas" to "mis notas",
            "Batería" to "batería",
            "Ayuda" to "ayuda")
        for (pair in examples.chunked(2)) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for ((caption, command) in pair) {
                row.addView(ChispaUi.button(this, caption).apply {
                    setOnClickListener {
                        entry.setText(command)
                        send()
                    }
                }, LinearLayout.LayoutParams(0, dp(45), 1f).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                })
            }
            welcome.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(4)
            })
        }
        history.addView(welcome, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, dp(8), 0, 0)
        })
    }

    private fun addBubble(message: String): TextView {
        val isUser = message.startsWith("Tú:")
        val body = message.removePrefix(if (isUser) "Tú:" else "Chispa:").trimStart()
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (isUser) Gravity.END else Gravity.START
            setPadding(0, dp(7), 0, dp(7))
        }
        if (!isUser) {
            line.addView(ChispaUi.avatar(this), LinearLayout.LayoutParams(dp(30), dp(30)).apply {
                rightMargin = dp(6)
                topMargin = dp(16)
            })
        }
        val stack = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        stack.addView(ChispaUi.label(this, if (isUser) "TÚ" else "CHISPA", 10f,
            ChispaUi.MUTED, true).apply {
            gravity = if (isUser) Gravity.END else Gravity.START
        }, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(5)
            leftMargin = dp(7)
            rightMargin = dp(7)
        })
        val bubble = ChispaUi.label(this, "", 15f, ChispaUi.TEXT).apply {
            background = ChispaUi.background(this@MainActivity,
                if (isUser) ChispaUi.USER else ChispaUi.SURFACE_ALT, 17)
            setPadding(dp(13), dp(12), dp(13), dp(12))
            setLineSpacing(dp(3).toFloat(), 1.04f)
            maxWidth = (resources.displayMetrics.widthPixels * 0.79).toInt()
            text = ChispaUi.markdown(body)
        }
        stack.addView(bubble)
        line.addView(stack, LinearLayout.LayoutParams(-2, -2))
        history.addView(line)
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        return bubble
    }

    private fun displayAnswer(target: TextView, full: String) {
        target.text = ChispaUi.markdown(full.removePrefix("Chispa:").trimStart())
    }

    private fun showToolsMenu() {
        val entries = arrayOf(
            "Mostrar personaje flotante",
            "Ocultar personaje flotante",
            "Respaldar modelo Qwen (.gguf)",
            "Restaurar modelo Qwen",
            "Exportar conversaciones y notas",
            "Importar conversaciones y notas",
            "Editar personalidad",
            "Acerca de Chispa"
        )
        AlertDialog.Builder(this)
            .setTitle("Herramientas de Chispa")
            .setItems(entries) { _, which ->
                when (which) {
                    0 -> startOverlay()
                    1 -> stopService(Intent(this, OverlayService::class.java))
                    2 -> backupModel()
                    3 -> restoreModel()
                    4 -> startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/json"
                        putExtra(Intent.EXTRA_TITLE, "chispa_recuerdos.json")
                    }, EXPORT_MEMORY_REQUEST)
                    5 -> startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/json"
                    }, IMPORT_MEMORY_REQUEST)
                    6 -> setPersonality()
                    7 -> AlertDialog.Builder(this)
                        .setTitle("Chispa Local")
                        .setMessage("Asistente experimental en Android, con Qwen 1.5B y memoria local. El modelo se ejecuta en el teléfono una vez descargado. Algunas funciones aún son prototipos.")
                        .setPositiveButton("Entendido", null).show()
                }
            }
            .show()
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
        val deep = modelReady && analysisMode.isChecked
        entry.setText("")
        val request = "Tú: " + msg
        addBubble(request)
        memory.addMessage(request)
        if (!modelReady || LocalCommands.isCommand(msg)) {
            val reply = "Chispa: " + LocalCommands.run(this, memory, msg)
            addBubble(reply)
            memory.addMessage(reply)
            return
        }

        val answerBubble = addBubble(if (deep) "Chispa: Analizando objetivos y condiciones..." else "Chispa: Pensando...")
        working = true
        modelButton.isEnabled = false
        analysisMode.isEnabled = false
        val begin = SystemClock.elapsedRealtime()
        generationJob = ui.launch {
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
                    ai.sendUserPrompt(analysisPrompt, predictLength = 128).collect { part ->
                        plan.append(part)
                    }
                    notes = plan.toString().take(1100)
                    answerBubble.text = "Chispa: Preparando la respuesta..."
                }
                val finalPrompt = if (deep) {
                    """SOLICITUD ORIGINAL:
                       |$msg
                       |Responde ahora a la solicitud original, sin omitir ningún requisito.
                       |Revisa tus cálculos y condiciones. Si no estás seguro, dilo.
                       |Presenta una solución ordenada con explicaciones útiles.
                       |Apóyate en estas notas de análisis:
                       |$notes""".trimMargin()
                } else msg
                status.text = if (deep) "Paso 2/2 • Desarrollando idea..." else "Generando respuesta..."
                ai.sendUserPrompt(finalPrompt, predictLength = if (deep) 352 else 224).collect { chunk ->
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
            } catch (e: CancellationException) {
                answerBubble.text = "Chispa: Respuesta detenida."
                status.text = "Respuesta detenida"
            } catch (e: Exception) {
                answerBubble.text = "Chispa: Ocurrió un error al generar: " + (e.message ?: "desconocido")
                status.text = "Error de generación. Intenta reiniciar el modelo."
            } finally {
                working = false
                generationJob = null
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
                            "Si no sabes algo, dilo. Responde de forma breve salvo que te pidan detalles. " +
                            "Personalidad elegida: " + prefs().getString("personality", "Cordial y claro").orEmpty().take(220) +
                            ". Memoria de notas y conversaciones previas: " + memory.memoriesForPrompt().take(1500)
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


    private fun backupModel() {
        if (!ModelVault.isPresent(this)) {
            Toast.makeText(this, "No hay modelo completo para respaldar.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, ModelVault.NAME)
        }
        startActivityForResult(intent, BACKUP_REQUEST)
    }

    private fun restoreModel() {
        if (working || modelReady) {
            Toast.makeText(this, "Reabre Chispa sin activar la IA antes de restaurar.", Toast.LENGTH_LONG).show()
            return
        }
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }, RESTORE_REQUEST)
    }

    @Deprecated("Actividad Android mínima sin AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != Activity.RESULT_OK) return
        if (requestCode == EXPORT_MEMORY_REQUEST || requestCode == IMPORT_MEMORY_REQUEST) {
            if (working) return
            working = true
            status.text = "Procesando respaldo de notas y conversaciones..."
            ui.launch {
                try {
                    val count = if (requestCode == EXPORT_MEMORY_REQUEST)
                        MemoryVault.backup(this@MainActivity, uri)
                    else MemoryVault.restore(this@MainActivity, uri)
                    status.text = "Recuerdos " +
                        (if (requestCode == EXPORT_MEMORY_REQUEST) "exportados: " else "restaurados: ") + count
                    if (requestCode == IMPORT_MEMORY_REQUEST) {
                        history.removeAllViews()
                        memory.messages().forEach { addBubble(it) }
                    }
                } catch(e: Exception) {
                    status.text = "Error de recuerdos: " + (e.message ?: "desconocido")
                } finally { working = false }
            }
            return
        }
        if (requestCode != BACKUP_REQUEST && requestCode != RESTORE_REQUEST) return
        if (working) return
        working = true
        modelButton.isEnabled = false
        status.text = "Copiando GGUF (~1 GB). Mantén la aplicación abierta."
        ui.launch {
            try {
                val digest = if (requestCode == BACKUP_REQUEST) {
                    ModelVault.backup(this@MainActivity, uri) { bytes ->
                        handler.post { status.text = "Respaldo: " + (bytes / 1_000_000L) + " MB" }
                    }
                } else {
                    ModelVault.restore(this@MainActivity, uri) { bytes ->
                        handler.post { status.text = "Restauración: " + (bytes / 1_000_000L) + " MB" }
                    }
                }
                if (requestCode == RESTORE_REQUEST)
                    prefs().edit().putBoolean("complete", true).remove("download_id").apply()
                status.text = "Copia terminada • SHA256 " + digest.take(12) + "..."
            } catch (e: Exception) {
                status.text = "Error al copiar: " + (e.message ?: "desconocido")
            } finally {
                working = false
                refreshModelButton()
            }
        }
    }

    private fun setPersonality() {
        val edit = EditText(this).apply {
            setText(prefs().getString("personality", "Cordial y claro"))
            setSingleLine(false)
            minLines = 2
            maxLines = 5
        }
        AlertDialog.Builder(this).setTitle("Personalidad de Chispa")
            .setMessage("Se aplicará después de reiniciar el motor de IA.")
            .setView(edit)
            .setPositiveButton("Guardar") { _, _ ->
                prefs().edit().putString("personality", edit.text.toString().take(220)).apply()
                status.text = "Personalidad guardada • reinicia el motor para aplicarla"
            }
            .setNegativeButton("Cancelar", null).show()
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
