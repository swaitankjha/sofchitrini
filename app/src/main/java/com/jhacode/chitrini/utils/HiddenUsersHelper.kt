package com.jhacode.chitrini.utils

import android.content.Context
import android.content.SharedPreferences

object HiddenUsersHelper {
    private const val PREFS_NAME = "chitrini_hidden_prefs"
    private const val HIDDEN_USERS_KEY = "hidden_usernames"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getHiddenUsers(context: Context): Set<String> {
        return getPrefs(context).getStringSet(HIDDEN_USERS_KEY, emptySet()) ?: emptySet()
    }

    fun setHiddenUsers(context: Context, usernames: Set<String>) {
        getPrefs(context).edit().putStringSet(HIDDEN_USERS_KEY, usernames).apply()
    }

    fun isUserHidden(context: Context, username: String): Boolean {
        return getHiddenUsers(context).contains(username)
    }
}
