package desktop

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal sealed interface InstanceStart {
    data class Primary(val instance: SingleInstance) : InstanceStart
    data object Activated : InstanceStart
    data object OtherSession : InstanceStart
    data object Unreachable : InstanceStart
}

internal class SingleInstance private constructor(
    private val lock: FileChannel,
    private val endpointPath: Path,
    private val server: ServerSocket,
    private val token: ByteArray,
) : AutoCloseable {
    private val requests = Channel<Unit>(Channel.CONFLATED)
    val activations: ReceiveChannel<Unit> get() = requests

    @Volatile
    var closing = false

    private val listener = thread(isDaemon = true, name = "StopBonus activation") {
        while (!server.isClosed) {
            try {
                server.accept().use(::receiveActivation)
            } catch (failure: IOException) {
                if (!server.isClosed) logger.debug("Could not receive activation request", failure)
            }
        }
    }

    fun requestActivation(): Boolean = !closing && requests.trySend(Unit).isSuccess

    private fun receiveActivation(socket: Socket) {
        socket.soTimeout = IO_TIMEOUT
        val input = socket.getInputStream()
        val supplied = ByteArray(TOKEN_SIZE)
        var offset = 0
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(IO_TIMEOUT.toLong())
        while (offset < supplied.size) {
            val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
            if (remaining <= 0) return
            socket.soTimeout = remaining.toInt()
            val count = input.read(supplied, offset, supplied.size - offset)
            if (count < 0) return
            offset += count
        }
        if (MessageDigest.isEqual(token, supplied)) {
            socket.getOutputStream().write(if (requestActivation()) ACCEPTED else CLOSING)
        }
    }

    override fun close() {
        closing = true
        requests.close()
        try {
            server.close()
            listener.join(IO_TIMEOUT.toLong() + 100)
            Files.deleteIfExists(endpointPath)
        } finally {
            lock.close()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SingleInstance::class.java)
        private val loopback = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        private const val IO_TIMEOUT = 500
        private const val TOKEN_SIZE = 32
        private const val PROTOCOL = 1
        private const val ACCEPTED = 1
        private const val CLOSING = 2
        private const val ENDPOINT_SIZE = Int.SIZE_BYTES * 3 + Long.SIZE_BYTES + TOKEN_SIZE

        fun start(directory: Path): InstanceStart {
            ensurePrivateDirectory(directory)
            val lockPath = directory.resolve("instance.lock")
            val endpointPath = directory.resolve("instance.endpoint")
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            val session = WindowsDesktop.sessionId(ProcessHandle.current().pid()) ?: -1

            do {
                val channel = FileChannel.open(lockPath, CREATE, WRITE, NOFOLLOW_LINKS)
                var primary = false
                try {
                    if (channel.tryLock() != null) {
                        val instance = publish(channel, endpointPath, session)
                        primary = true
                        return InstanceStart.Primary(instance)
                    }
                } finally {
                    if (!primary) channel.close()
                }

                val endpoint = readEndpoint(endpointPath)
                if (endpoint != null) {
                    if (session >= 0 && endpoint.session >= 0 && session != endpoint.session &&
                        WindowsDesktop.sessionId(endpoint.pid) == endpoint.session
                    ) {
                        return InstanceStart.OtherSession
                    }
                    if (notifyPrimary(endpoint, deadline)) return InstanceStart.Activated
                }
                val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
                if (remaining > 0) Thread.sleep(minOf(75, remaining))
            } while (System.nanoTime() < deadline)
            return InstanceStart.Unreachable
        }

        private fun publish(lock: FileChannel, endpointPath: Path, session: Int): SingleInstance {
            Files.deleteIfExists(endpointPath)
            val server = ServerSocket()
            var instance: SingleInstance? = null
            try {
                server.bind(InetSocketAddress(loopback, 0), 16)
                val token = ByteArray(TOKEN_SIZE).also(SecureRandom()::nextBytes)
                instance = SingleInstance(lock, endpointPath, server, token)
                val endpoint = ByteBuffer.allocate(ENDPOINT_SIZE)
                    .putInt(PROTOCOL)
                    .putInt(server.localPort)
                    .putLong(ProcessHandle.current().pid())
                    .putInt(session)
                    .put(token)
                val temporary = Files.createTempFile(endpointPath.parent, "endpoint-", ".tmp")
                try {
                    Files.write(temporary, endpoint.array())
                    Files.move(temporary, endpointPath, ATOMIC_MOVE)
                } finally {
                    Files.deleteIfExists(temporary)
                }
                return instance
            } catch (failure: Exception) {
                if (instance == null) server.close() else instance.close()
                throw failure
            }
        }

        private data class Endpoint(val port: Int, val pid: Long, val session: Int, val token: ByteArray)

        private fun readEndpoint(path: Path): Endpoint? {
            return try {
                Files.newInputStream(path, NOFOLLOW_LINKS).use { input ->
                    val bytes = input.readNBytes(ENDPOINT_SIZE + 1)
                    if (bytes.size != ENDPOINT_SIZE) return null
                    val data = ByteBuffer.wrap(bytes)
                    if (data.int != PROTOCOL) return null
                    val port = data.int
                    val pid = data.long
                    val session = data.int
                    if (port !in 1..65535 || pid <= 0) return null
                    Endpoint(port, pid, session, ByteArray(TOKEN_SIZE).also { data.get(it) })
                }
            } catch (_: IOException) {
                null
            }
        }

        private fun notifyPrimary(endpoint: Endpoint, deadline: Long): Boolean {
            return try {
                Socket().use { socket ->
                    val connectTime = remainingTimeout(deadline)
                    if (connectTime == 0) return false
                    socket.connect(InetSocketAddress(loopback, endpoint.port), connectTime)
                    val readTime = remainingTimeout(deadline)
                    if (readTime == 0) return false
                    socket.soTimeout = readTime
                    WindowsDesktop.allowForeground(endpoint.pid)
                    socket.getOutputStream().write(endpoint.token)
                    socket.getInputStream().read() == ACCEPTED
                }
            } catch (_: IOException) {
                false
            }
        }

        private fun remainingTimeout(deadline: Long): Int =
            TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()).coerceIn(0, IO_TIMEOUT.toLong()).toInt()

        private fun ensurePrivateDirectory(directory: Path) {
            if (WindowsDesktop.isWindows) {
                WindowsDesktop.ensurePrivateDirectory(directory)
                return
            }
            val privatePermissions = PosixFilePermissions.fromString("rwx------")
            try {
                Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(privatePermissions))
            } catch (_: FileAlreadyExistsException) {
                // Both launchers may reach directory creation at the same time.
            }
            check(Files.isDirectory(directory, NOFOLLOW_LINKS)) { "Invalid runtime directory: $directory" }
            val user = directory.fileSystem.userPrincipalLookupService
                .lookupPrincipalByName(System.getProperty("user.name"))
            check(Files.getOwner(directory, NOFOLLOW_LINKS) == user &&
                Files.getPosixFilePermissions(directory, NOFOLLOW_LINKS) == privatePermissions
            ) { "Runtime directory is not private: $directory" }
        }
    }
}
