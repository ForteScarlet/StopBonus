package desktop

import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingleInstanceTest {
    @get:Rule
    val temporary = TemporaryFolder()

    /** A second JVM activates the first, and a new launch can replace stale metadata after a crash. */
    @Test(timeout = 30_000)
    fun activationAndCrashRecovery() {
        val directory = temporary.root.toPath().resolve("runtime")
        val primary = launcher(directory)
        try {
            val output = primary.inputReader()
            assertEquals("PRIMARY", output.readLine())
            val secondary = launcher(directory)
            try {
                assertTrue(secondary.waitFor(8, TimeUnit.SECONDS))
                assertEquals("ACTIVATED", secondary.inputReader().readLine())
                assertEquals("ACTIVATION", output.readLine())
            } finally {
                secondary.destroyForcibly()
            }
        } finally {
            primary.destroyForcibly()
            assertTrue(primary.waitFor(5, TimeUnit.SECONDS))
        }

        assertTrue(Files.exists(directory.resolve("instance.endpoint")))
        val restarted = launcher(directory, "exit")
        try {
            assertTrue(restarted.waitFor(8, TimeUnit.SECONDS))
            assertEquals("PRIMARY", restarted.inputReader().readLine())
            assertFalse(Files.exists(directory.resolve("instance.endpoint")))
            assertTrue(Files.exists(directory.resolve("instance.lock")))
        } finally {
            restarted.destroyForcibly()
        }
    }

    private fun launcher(directory: Path, vararg arguments: String): Process {
        val dependencies = listOf(
            InstanceProbe::class.java, SingleInstance::class.java, Unit::class.java,
            Channel::class.java, LoggerFactory::class.java, Native::class.java, User32::class.java,
        )
        val classpath = dependencies.map { Path.of(it.protectionDomain.codeSource.location.toURI()) }
            .distinct().joinToString(File.pathSeparator)
        val executable = if (WindowsDesktop.isWindows) "java.exe" else "java"
        val java = Path.of(System.getProperty("java.home"), "bin", executable)
        return ProcessBuilder(java.toString(), "-cp", classpath, InstanceProbe::class.java.name, directory.toString(), *arguments)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
    }
}

internal object InstanceProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        when (val started = SingleInstance.start(Path.of(args[0]))) {
            is InstanceStart.Primary -> started.instance.use { instance ->
                println("PRIMARY")
                if (args.getOrNull(1) != "exit") {
                    runBlocking {
                        instance.activations.receive()
                        println("ACTIVATION")
                    }
                    System.`in`.read()
                }
            }
            InstanceStart.Activated -> println("ACTIVATED")
            else -> error("Unexpected start result $started")
        }
    }
}
