import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import kotlin.io.path.Path

fun main() {

    System.getenv().forEach { (k, v) ->
        println("$k:\t$v")
    }

    println("====")

    System.getProperties().list(System.out)


}
