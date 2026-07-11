package com.jpj.presenterproapp

import android.content.Context
import java.lang.ref.WeakReference

object AppContext {
    private var contextRef: WeakReference<Context>? = null

    var context: Context?
        get() = contextRef?.get()
        set(value) {
            contextRef = value?.let { WeakReference(it.applicationContext) }
        }
}
