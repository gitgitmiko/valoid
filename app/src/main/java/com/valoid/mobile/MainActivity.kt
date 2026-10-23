package com.valoid.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.valoid.mobile.notify.StoreCheckWorker
import com.valoid.mobile.ui.ValoidApp
import com.valoid.mobile.ui.ValoidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StoreCheckWorker.schedule(this)
        setContent {
            ValoidTheme { ValoidApp() }
        }
    }
}
