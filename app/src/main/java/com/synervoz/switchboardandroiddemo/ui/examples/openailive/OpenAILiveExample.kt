package com.synervoz.switchboardandroiddemo.ui.examples.openailive

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.synervoz.switchboard.sdk.Switchboard
import com.synervoz.switchboard.sdk.SwitchboardResult
import org.json.JSONObject

private const val ENGINE_JSON = "OpenAILiveExample.json"
private const val LIVE_NODE = "liveNode"
private const val MUSIC_PLAYER_NODE = "musicPlayerNode"
private const val MUSIC_URL =
    "https://switchboard-sdk-public.s3.dualstack.us-east-1.amazonaws.com/assets/audio/music/elevator-music-stereo.mp3"

// States in which the music is open, so play resumes it instead of loading it again.
private val OPEN_MUSIC_STATES = setOf("connecting", "buffering", "ready")

private val BLUETOOTH_HEADSETS = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET)
private val WIRED_HEADSETS = setOf(
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_USB_HEADSET,
)

/**
 * A voice conversation with OpenAI's realtime model, through the OpenAI.Live node.
 *
 * Each [start] creates the engine from OpenAILiveExample.json and starts a new conversation;
 * [stop] ends it. The assistant's tools (play and pause the music) are answered here,
 * in [runTool].
 */
class OpenAILiveExample(context: Context) {
    interface Listener {
        /** A piece of what [speaker] ("You" or "Assistant") said. */
        fun onTranscript(speaker: String, delta: String)

        /** A line about the session or a tool call. */
        fun onStatus(text: String)
    }

    /** Called on the main thread. */
    var listener: Listener? = null

    val isRunning: Boolean
        get() = engineId != null

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var engineId: String? = null
    private val eventListeners = mutableListOf<Int>()

    fun start() {
        if (isRunning) return
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            status("Allow microphone access in the app's settings, then try again.")
            return
        }

        val engineJson = JSONObject(appContext.assets.open(ENGINE_JSON).bufferedReader().use { it.readText() })
        // Loudspeaker only when no headset is used.
        val headsetUsed = useBluetoothHeadset() || connectedHeadset(WIRED_HEADSETS) != null
        if (!headsetUsed) {
            engineJson.getJSONObject("config").put("speakerphoneOn", true)
        }
        val createResult = Switchboard.createEngine(engineJson.toString())
        val id = createResult.value
        if (createResult.isError || id == null) {
            status("Failed to create the engine: ${createResult.error}")
            return
        }
        engineId = id
        addEventListeners()

        ContextCompat.startForegroundService(appContext, Intent(appContext, OpenAILiveService::class.java))
        val startResult = Switchboard.callAction(id, "start")
        if (startResult.isError) {
            status("Failed to start the engine: ${startResult.error}")
            stop()
            return
        }
        status("Connecting…")
    }

    fun stop() {
        val id = engineId ?: return
        Switchboard.callAction(id, "stop")
        eventListeners.forEach { Switchboard.removeEventListener(LIVE_NODE, it) }
        eventListeners.clear()
        Switchboard.destroyEngine(id)
        engineId = null
        appContext.startService(Intent(appContext, OpenAILiveService::class.java).setAction(OpenAILiveService.ACTION_STOP))
        restoreAudioMode()
    }

    private fun addEventListeners() {
        fun listen(eventName: String, handler: (Map<*, *>) -> Unit) {
            Switchboard.addEventListener(LIVE_NODE, eventName) { _, eventData ->
                handler(eventData as? Map<*, *> ?: emptyMap<String, Any>())
            }.value?.let { eventListeners.add(it) }
        }

        // Transcript
        listen("inputTranscriptDelta") { transcript("You", it["delta"] as? String ?: "") }
        listen("outputTranscriptDelta") { transcript("Assistant", it["delta"] as? String ?: "") }

        // Tool calls. Events arrive on an SDK thread, which may answer right away.
        listen("toolCall") { data ->
            val callId = data["callId"] as? String ?: return@listen
            val name = data["name"] as? String ?: return@listen
            val error = runTool(name)
            val answer = if (error == null) {
                status("[$name]")
                Switchboard.callAction(LIVE_NODE, "submitToolResult", mapOf("callId" to callId, "outputJson" to """{"ok":true}"""))
            } else {
                status("[$name failed: $error]")
                val errorJson = JSONObject().put("error", error).toString()
                Switchboard.callAction(LIVE_NODE, "submitToolError", mapOf("callId" to callId, "errorJson" to errorJson))
            }
            if (answer.isError) {
                status("[Failed to answer $name: ${answer.error}]")
            }
        }

        // Session state
        listen("sessionStarted") {
            status(if (it["continued"] == true) "Connected." else "Connected. Start talking.")
        }
        listen("sessionPaused") { status("Standby: paused after 15 s of quiet. Speak to resume.") }
        listen("sessionResumed") { status("Standby: resumed.") }
        listen("sessionIdle") {
            status("Idle: the session closed after 30 s of quiet. Speak to continue the conversation.")
        }
        listen("sessionWoken") { status("Idle: reopened.") }
        listen("sessionDisconnected") {
            status(if (it["reconnecting"] == true) "Connection lost. Reconnecting." else "Session refused. Press Stop, then Start to try again.")
        }
        listen("error") { data ->
            status("Error: ${data["type"]} ${data["code"]}: ${data["message"]}")
        }
    }

    // Tools

    // Runs a tool: null when it worked, otherwise the error.
    private fun runTool(name: String): String? {
        val result = when (name) {
            "play_music" -> playMusic()
            "pause_music" -> Switchboard.callAction(MUSIC_PLAYER_NODE, "pause")
            else -> return "Unknown tool: $name"
        }
        return if (result.isError) result.error ?: "Unknown error" else null
    }

    // Resumes the track if it's open (paused), otherwise loads it, which starts it: the player plays what it loads.
    private fun playMusic(): SwitchboardResult<*> {
        val state = Switchboard.getValue(MUSIC_PLAYER_NODE, "state").value as? String
        return if (state in OPEN_MUSIC_STATES) {
            Switchboard.callAction(MUSIC_PLAYER_NODE, "play")
        } else {
            Switchboard.callAction(MUSIC_PLAYER_NODE, "load", mapOf("url" to MUSIC_URL))
        }
    }

    // Audio routing, chosen once at start. Headsets connected or disconnected during the
    // conversation need Stop and Start.

    private fun connectedHeadset(types: Set<Int>): AudioDeviceInfo? =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type in types }

    // Android picks wired headsets itself; a Bluetooth one must be selected, and its call audio
    // may take a moment to come up, or never does. Waits up to 3 s; false means not used.
    @Suppress("DEPRECATION")
    private fun useBluetoothHeadset(): Boolean {
        val headset = connectedHeadset(BLUETOOTH_HEADSETS) ?: return false
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.setCommunicationDevice(headset)
        } else {
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
        }
        repeat(30) {
            if (bluetoothCallAudioOn()) return true
            Thread.sleep(100)
        }
        restoreAudioMode()
        return false
    }

    private fun bluetoothCallAudioOn(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.communicationDevice?.type in BLUETOOTH_HEADSETS
        } else {
            appContext.registerReceiver(null, IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED))
                ?.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1) == AudioManager.SCO_AUDIO_STATE_CONNECTED
        }

    // The audio mode and route are process-wide, so other examples would inherit them.
    @Suppress("DEPRECATION")
    private fun restoreAudioMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.isSpeakerphoneOn = false
        }
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    // UI updates go to the main thread.

    private fun transcript(speaker: String, delta: String) {
        mainHandler.post { listener?.onTranscript(speaker, delta) }
    }

    private fun status(text: String) {
        mainHandler.post { listener?.onStatus(text) }
    }
}
