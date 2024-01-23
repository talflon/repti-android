package net.getzit.repti

import android.app.Application

class ReptiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TaskRepository.instance = TaskRepository.create(applicationContext)
    }
}