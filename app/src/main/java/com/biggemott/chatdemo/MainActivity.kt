package com.biggemott.chatdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.biggemott.chatdemo.ui.chat.ChatRoute
import com.biggemott.chatdemo.ui.chat.ChatViewModel
import com.biggemott.chatdemo.ui.theme.ChatDemoTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val viewModel = ViewModelProvider(this)[ChatViewModel::class.java]
        setContent {
            ChatDemoTheme {
                ChatRoute(viewModel, onBack = onBackPressedDispatcher::onBackPressed)
            }
        }
    }
}
