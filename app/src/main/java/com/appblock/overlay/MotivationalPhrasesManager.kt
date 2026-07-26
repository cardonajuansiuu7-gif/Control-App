package com.appblock.overlay

import android.content.Context

object MotivationalPhrasesManager {

    private const val KEY_CUSTOM_PHRASES = "custom_motivational_phrases"

    private val defaultPhrases = listOf(
        "Cada minuto que no usás esta app es un minuto que ganás para vos.",
        "Tu enfoque de hoy construye tu futuro de mañana.",
        "Podés soltar el teléfono. Ya demostraste que podés.",
        "Lo que estás evitando ahora vale más que lo que estás mirando.",
        "Un paso lejos de la pantalla es un paso hacia tu meta."
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)

    fun getAllPhrases(context: Context): List<String> {
        val custom = prefs(context).getStringSet(KEY_CUSTOM_PHRASES, emptySet()) ?: emptySet()
        return defaultPhrases + custom.toList()
    }

    fun getCustomPhrases(context: Context): List<String> {
        return (prefs(context).getStringSet(KEY_CUSTOM_PHRASES, emptySet()) ?: emptySet()).toList()
    }

    fun addPhrase(context: Context, phrase: String) {
        val trimmed = phrase.trim()
        if (trimmed.isEmpty()) return
        val current = (prefs(context).getStringSet(KEY_CUSTOM_PHRASES, emptySet()) ?: emptySet()).toMutableSet()
        current.add(trimmed)
        prefs(context).edit().putStringSet(KEY_CUSTOM_PHRASES, current).apply()
    }

    fun removePhrase(context: Context, phrase: String) {
        val current = (prefs(context).getStringSet(KEY_CUSTOM_PHRASES, emptySet()) ?: emptySet()).toMutableSet()
        current.remove(phrase)
        prefs(context).edit().putStringSet(KEY_CUSTOM_PHRASES, current).apply()
    }

    fun getRandomPhrase(context: Context): String {
        val all = getAllPhrases(context)
        return if (all.isEmpty()) "¡Seguís avanzando!" else all.random()
    }
}
