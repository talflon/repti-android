// SPDX-FileCopyrightText: 2024 Daniel Getz <dan@getzit.net>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package net.getzit.repti

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import net.getzit.repti.ui.AboutUI

class AboutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AboutUI()
        }
    }
}