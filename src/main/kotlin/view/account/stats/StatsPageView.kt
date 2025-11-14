package view.account.stats

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import love.forte.bonus.bonus_self_desktop.generated.resources.Res
import love.forte.bonus.bonus_self_desktop.generated.resources.icon_kid_star
import org.jetbrains.compose.resources.painterResource
import view.account.PageViewState
import view.account.SimpleAccountViewPageSelector
import view.account.stats.StatsType.COUNT
import java.time.Year
import java.time.YearMonth


/**
 * 统计页
 *
 * @author ForteScarlet
 */
object StatsPageView : SimpleAccountViewPageSelector {

    override val isMenuIconSupport: Boolean
        get() = true

    @Composable
    override fun menuIcon(state: PageViewState) {
        Icon(painterResource(Res.drawable.icon_kid_star), "icon_star")
    }

    @Composable
    override fun menuLabel(state: PageViewState) {
        Text("统计")
    }

    @Composable
    override fun rightView(state: PageViewState) {
        MainStatsPageView(state)
    }
}

abstract class StatsMode {
    @Composable
    open fun Icon(state: PageViewState) {
    }

    @Composable
    open fun Label(state: PageViewState) {
    }

    @Composable
    open fun TopBar(state: PageViewState) {
    }

    @Composable
    open fun BottomBar(state: PageViewState) {
    }

    @Composable
    open fun Content(state: PageViewState) {
    }
}

enum class StatsType(val title: String) {
    COUNT("次数统计"),
    DURATION("时长统计"),
}

@Composable
private fun MainStatsPageView(state: PageViewState) {
    val modeTypeState = remember { mutableStateOf(COUNT) }
    val modes = remember {
        listOf(
            MonthDailyModeStats(MonthDailyModeState(YearMonth.now(), modeTypeState)),
            YearMonthlyModeStats(YearMonthlyModeState(Year.now(), modeTypeState)),
        )
    }

    var selected by remember { mutableStateOf(modes.first()) }


    PermanentNavigationDrawer(
        drawerContent = {
            PermanentDrawerSheet(
                modifier = Modifier.fillMaxWidth(.16f)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (mode in modes) {
                        NavigationDrawerItem(
                            selected = selected == mode,
                            label = { mode.Label(state) },
                            onClick = {
                                selected = mode
                            }
                        )
                    }
                }
            }
        },
    ) {
        Crossfade(selected) { selected ->
            Scaffold(
                topBar = {
                    selected.TopBar(state)
                },
                bottomBar = {
                    selected.BottomBar(state)
                }
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    selected.Content(state)
                }
            }
        }
    }
}



