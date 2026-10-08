package com.meltronics.ojtodada

import android.app.Application
import com.meltronics.ojtodada.core.Engine

class OjApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Engine.init(this)
    }
}
