package view.account

import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.runtime.Composable


interface AccountViewPageSelector {
    /**
     * 左侧菜单列表中的元素，
     * 并用于选择一个page。
     */
    @Composable
    fun navigationDrawerItem(
        state: PageViewState,
        selected: AccountViewPage?,
        onSelect: (AccountViewPage?) -> Unit
    )

}

/**
 *
 * @author ForteScarlet
 */
interface AccountViewPage {
    /**
     * 右侧主要视图。
     */
    @Composable
    fun rightView(state: PageViewState)
}

/**
 * Selector 和 page 结合在一起
 */
interface SimpleAccountViewPageSelector : AccountViewPageSelector, AccountViewPage {
    @Composable
    override fun navigationDrawerItem(
        state: PageViewState,
        selected: AccountViewPage?,
        onSelect: (AccountViewPage?) -> Unit
    ) {
        NavigationDrawerItem(
            selected = this == selected,
            onClick = { onSelect(if (this == selected) null else this) },
            icon = if (isMenuIconSupport) {
                {
                    menuIcon(state)
                }
            } else null,
            label = { menuLabel(state) }
        )
    }

    val isMenuIconSupport: Boolean
        get() = false

    @Composable
    fun menuIcon(state: PageViewState) {
    }

    @Composable
    fun menuLabel(state: PageViewState)
}



