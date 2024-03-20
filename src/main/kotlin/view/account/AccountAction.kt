package view.account

import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable

enum class AccountAction(val title: String, val icon: @Composable () -> Unit) {
    Home("首页", { Icon(Icons.Filled.Home, "首页") }),
    Weapon("武器库", { Icon(Icons.Filled.Face, "武器库") }),

    ;
}
