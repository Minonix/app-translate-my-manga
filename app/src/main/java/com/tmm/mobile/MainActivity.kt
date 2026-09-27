package com.tmm.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.tmm.mobile.ui.nav.TmmRoot
import com.tmm.mobile.ui.theme.TmmTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TmmTheme { TmmRoot() }
        }
    }
}
