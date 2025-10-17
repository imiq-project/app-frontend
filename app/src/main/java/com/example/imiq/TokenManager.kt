package com.example.imiq

import android.content.Context
import android.content.SharedPreferences

object TokenManager {
    private const val PREF_NAME = "imiq_prefs"
    private const val KEY_TOKEN = "auth_token"

    private var sharedPreferences: SharedPreferences? = null

    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveToken(token: String) {
        sharedPreferences?.edit()?.apply {
            putString(KEY_TOKEN, token)
            apply()
        }
    }

    fun getToken(): String? {
        return sharedPreferences?.getString(KEY_TOKEN, null)
    }

    fun clearToken() {
        sharedPreferences?.edit()?.apply {
            remove(KEY_TOKEN)
            apply()
        }
    }

    fun isLoggedIn(): Boolean {
        return getToken() != null
    }
}

