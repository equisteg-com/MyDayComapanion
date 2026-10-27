package com.equisteg.mydaycompanion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.equisteg.mydaycompanion.navigation.AppNavigation
import com.equisteg.mydaycompanion.ui.MainViewModel
import com.equisteg.mydaycompanion.ui.theme.MyDayCompanionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val services = (application as MyDayCompanionApp).services
        val viewModel = ViewModelProvider(this, MainViewModel.factory(services))[MainViewModel::class.java]
        setContent {
            MyDayCompanionTheme {
                AppNavigation(viewModel)
            }
        }
    }
}
