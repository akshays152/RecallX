package com.recallx
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.recallx.core.ui.RecallXTheme
import com.recallx.navigation.RecallXNavHost
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RecallXTheme { Surface { RecallXNavHost(this) } } }
    }
}
