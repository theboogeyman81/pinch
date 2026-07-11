package com.pinch.gary.core.util

import android.util.Log

/**
 * Thin wrapper around [Log] that only accepts [String] messages and scalar
 * metadata — there is no overload that takes a ByteArray, InputStream, or
 * Bitmap. This is a deliberate guardrail: per CLAUDE.md's privacy rules,
 * raw camera frames and audio PCM must never be written anywhere, including
 * Logcat. If a call site needs to log something about a frame, it must
 * extract a String/Int first (e.g. frame size, not frame bytes).
 */
object Logger {

    fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }
}
