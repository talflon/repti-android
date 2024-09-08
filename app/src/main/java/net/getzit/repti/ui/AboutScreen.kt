/*
 * SPDX-FileCopyrightText: 2024 Daniel Getz <dan@getzit.net>
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.getzit.repti.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import net.getzit.repti.BuildConfig
import net.getzit.repti.R
import net.getzit.repti.ui.theme.ReptiTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(goBack: () -> Unit) {
    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = {
            Text(stringResource(R.string.title_about))
        }, navigationIcon = {
            IconButton(onClick = goBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cmd_back)
                )
            }
        })
    }) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null)
                Text(
                    stringResource(R.string.app_name),
                    style = typography.displayLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.lbl_version) + ": " + BuildConfig.VERSION_NAME,
                    style = typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                stringResource(R.string.msg_copyright_notice),
                style = typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            HorizontalDivider()
            Text(
                stringResource(R.string.msg_about_licenses),
                style = typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            LibrariesContainer()
        }
    }
}

@Preview(name = "Light Mode", showBackground = true, showSystemUi = true)
@Preview(name = "Dark Mode", uiMode = UI_MODE_NIGHT_YES, showBackground = true, showSystemUi = true)
@Composable
fun PreviewAboutUI() {
    ReptiTheme(dynamicColor = false) {
        AboutScreen(goBack = {})
    }
}
