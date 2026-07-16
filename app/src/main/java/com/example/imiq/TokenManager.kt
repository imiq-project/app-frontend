package com.example.imiq

import android.content.Context
import android.content.SharedPreferences
import java.io.File

object TokenManager {
    private const val PREF_NAME = "imiq_prefs"
    private const val KEY_TOKEN = "auth_token"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_AGE = "user_age"
    private const val KEY_PROFILE_TYPE = "profile_type"
    private const val KEY_PROFILE_COMPLETED = "profile_completed"
    private const val KEY_LANGUAGE = "app_language"

    private var sharedPreferences: SharedPreferences? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveToken(token: String) {
        sharedPreferences?.edit()?.putString(KEY_TOKEN, token)?.apply()
    }

    fun getToken(): String? {
        return sharedPreferences?.getString(KEY_TOKEN, null)
    }

    fun isLoggedIn(): Boolean {
        return getToken() != null
    }

    // ========== USER PROFILE ==========

    fun saveUserProfile(name: String, age: String, profileType: String) {
        sharedPreferences?.edit()?.apply {
            putString(KEY_USER_NAME, name)
            putString(KEY_USER_AGE, age)
            putString(KEY_PROFILE_TYPE, profileType)
            putBoolean(KEY_PROFILE_COMPLETED, true)
            apply()
        }
    }

    fun getUserName(): String? = sharedPreferences?.getString(KEY_USER_NAME, null)

    fun getUserAge(): String? = sharedPreferences?.getString(KEY_USER_AGE, null)

    fun getProfileType(): String? = sharedPreferences?.getString(KEY_PROFILE_TYPE, null)

    fun isProfileCompleted(): Boolean = sharedPreferences?.getBoolean(KEY_PROFILE_COMPLETED, false) ?: false

    fun clearProfile() {
        sharedPreferences?.edit()?.apply {
            remove(KEY_USER_NAME)
            remove(KEY_USER_AGE)
            remove(KEY_PROFILE_TYPE)
            remove(KEY_PROFILE_COMPLETED)
            apply()
        }
        // Also delete cognitive passport file
        appContext?.let {
            val file = File(it.filesDir, "cognitive_passport.json")
            if (file.exists()) file.delete()
        }
    }

    // ========== APP LANGUAGE ==========

    fun getLanguage(): String? = sharedPreferences?.getString(KEY_LANGUAGE, null)

    fun setLanguage(code: String) {
        sharedPreferences?.edit()?.putString(KEY_LANGUAGE, code)?.apply()
    }

    // ========== COGNITIVE PASSPORT ==========

    fun saveCognitivePassportFromTemplate() {
        appContext?.let { ctx ->
            val json = ctx.resources.openRawResource(R.raw.cognitive_passport_template)
                .bufferedReader().use { it.readText() }
            File(ctx.filesDir, "cognitive_passport.json").writeText(json)
        }
    }
}
