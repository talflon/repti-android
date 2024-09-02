// SPDX-FileCopyrightText: 2024 Daniel Getz <dan@getzit.net>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package net.getzit.repti

import android.app.Application

class ReptiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TaskRepository.instance = TaskRepository.create(applicationContext)
    }
}