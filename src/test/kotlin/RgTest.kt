import androidx.compose.ui.text.buildAnnotatedString
import java.time.Duration

fun main() {
    val d = Duration.ofHours(25).plusMinutes(90)

    println(d.toDays())
    println(d.toDaysPart())
    println(d.seconds)
    println(d.toSecondsPart())
    println(d.toHours())
    println(d.toHoursPart())
    println(d.toMinutes())
    println(d.toMinutesPart())

}
