package com.synervoz.switchboardandroiddemo

import android.app.Application
import com.synervoz.switchboard.sdk.Switchboard
import com.synervoz.switchboardopenai.OpenAIExtension
import com.synervoz.switchboardsherpa.SherpaExtension
import com.synervoz.switchboardsilerovad.SileroVADExtension
import com.synervoz.switchboardwhisper.WhisperExtension

// Your Switchboard app's ID and secret, for OpenAI Live; the other examples run without them.
// Register an app at https://console.switchboard.audio/register.
const val SWITCHBOARD_APP_ID = ""
const val SWITCHBOARD_APP_SECRET = ""

/**
 * Loads the Switchboard extensions and initializes the SDK exactly once per process.
 *
 * Initialization must happen a single time for the whole app — re-registering the
 * extensions (e.g. by initializing again for each example screen) makes
 * [Switchboard.initialize] return an error. Doing it here, in Application.onCreate,
 * guarantees a single initialization even if the activity is recreated.
 */
class DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        WhisperExtension.load()
        SileroVADExtension.load()
        SherpaExtension.load()
        OpenAIExtension.load()

        val initResult = Switchboard.initialize(
            context = this,
            appId = SWITCHBOARD_APP_ID,
            appSecret = SWITCHBOARD_APP_SECRET,
            extensions = mapOf(
                "Whisper" to emptyMap<String, Any>(),
                "Silero" to emptyMap<String, Any>(),
                "Sherpa" to emptyMap<String, Any>(),
                "OpenAI" to emptyMap<String, Any>(),
            ),
        )

        if (initResult.isError) {
            throw RuntimeException("Failed to initialize Switchboard SDK: ${initResult.error}")
        }
    }
}
