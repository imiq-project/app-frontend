package com.example.imiq

import android.content.Context
import java.io.File

/**
 * Stores the user's cognitive passport as raw JSON in the app's internal
 * private storage. One passport per user; saving overwrites the previous.
 * The file lives in /data/data/com.example.imiq/files/cognitive_passport.json
 * and is only readable by this app.
 */
object PassportStore {
    private const val FILENAME = "cognitive_passport.json"
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun file(): File {
        val ctx = appContext ?: error("PassportStore.init(context) must be called first")
        return File(ctx.filesDir, FILENAME)
    }

    /** Save the passport JSON exactly as returned by the Spark. */
    fun save(passportJson: String) {
        file().writeText(passportJson, Charsets.UTF_8)
    }

    /** Load the last saved passport JSON, or null if none saved. */
    fun load(): String? {
        val f = file()
        return if (f.exists()) f.readText(Charsets.UTF_8) else null
    }

    /** True if this device has a saved passport. */
    fun hasPassport(): Boolean = file().exists()

    /** Wipe the saved passport (e.g., when user re-runs the questionnaire). */
    fun clear() {
        val f = file()
        if (f.exists()) f.delete()
    }
}