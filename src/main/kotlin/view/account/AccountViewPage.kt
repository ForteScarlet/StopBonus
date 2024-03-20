package view.account

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable


/**
 *
 * @author ForteScarlet
 */
interface AccountViewPage {
    val isMenuIconSupport: Boolean
        get() = false

    @Composable
    fun menuIcon(state: PageViewState) {
    }

    @Composable
    fun menuLabel(state: PageViewState)

    /**
     * 右侧主要视图。
     */
    @Composable
    fun rightView(state: PageViewState)

}
