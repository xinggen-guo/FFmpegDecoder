package com.audio.study.ffmpegdecoder.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes


/**
 * @author xinggen.guo
 * @date 2025/11/14 16:38
 * @description
 */

/**
 * Global Toast helper for Kotlin.
 *
 * Usage:
 *  1) Initialize once in Application:
 *         ToastUtils.init(this)
 *
 *  2) Show toast anywhere:
 *         ToastUtils.showShort("Hello")
 *         ToastUtils.showLong(R.string.msg)
 *
 * Works from ANY thread. Thread-safe.
 */
object ToastUtils {

    private lateinit var appContext: Context
    private val mainHandler = Handler(Looper.getMainLooper())

    // Optional: keep a single Toast instance to avoid stacking
    private var toast: Toast? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun showShort(text: CharSequence?) {
        text ?: return
        show(text, Toast.LENGTH_SHORT)
    }

    fun showLong(text: CharSequence?) {
        text ?: return
        show(text, Toast.LENGTH_LONG)
    }

    fun showShort(@StringRes resId: Int) {
        show(appContext.getString(resId), Toast.LENGTH_SHORT)
    }

    fun showLong(@StringRes resId: Int) {
        show(appContext.getString(resId), Toast.LENGTH_LONG)
    }

    private fun show(text: CharSequence, duration: Int) {
        // If on main thread, show immediately
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showInternal(text, duration)
        } else {
            // Otherwise post to main thread
            mainHandler.post {
                showInternal(text, duration)
            }
        }
    }

    private fun showInternal(text: CharSequence, duration: Int) {
        toast?.cancel()
        toast = Toast.makeText(appContext, text, duration)
        toast?.show()
    }
}