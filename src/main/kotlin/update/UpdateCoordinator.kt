package update

import dev.hydraulic.conveyor.control.SoftwareUpdateController
import dev.hydraulic.conveyor.control.SoftwareUpdateController.Availability.AVAILABLE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Instant

internal data class UpdateState(
    val current: UpdateVersion,
    val checkedAt: Instant? = null,
    val newer: UpdateVersion? = null,
    val checking: Boolean = false,
    val verifying: Boolean = false,
    val confirmation: UpdateVersion? = null,
    val installing: Boolean = false,
    val handedOff: Boolean = false,
    val checkError: String? = null,
    val actionError: String? = null,
)

internal class UpdateCoordinator(
    private val scope: CoroutineScope,
    val debug: Boolean,
    private val repository: UpdateRepository = UpdateRepository(),
) {
    private val logger = LoggerFactory.getLogger(UpdateCoordinator::class.java)
    private val controller = if (debug) null else try {
        SoftwareUpdateController.getInstance()
    } catch (failure: Exception) {
        logger.warn("Could not load the update controller", failure)
        null
    } catch (failure: java.util.ServiceConfigurationError) {
        logger.warn("Could not load the update controller", failure)
        null
    }

    private val mutableState = MutableStateFlow(UpdateState(repository.current))
    val state = mutableState.asStateFlow()

    private var activeCheck: Deferred<UpdateVersion?>? = null
    val canInstall: Boolean
        get() = try {
            controller?.canTriggerUpdateCheckUI() == AVAILABLE
        } catch (failure: Exception) {
            logger.debug("Could not determine installer availability", failure)
            false
        } catch (failure: LinkageError) {
            logger.warn("Could not load the native installer", failure)
            false
        }

    fun checkAutomatically(enabled: Boolean) {
        if (!debug && enabled) check()
    }

    fun check() {
        val current = state.value
        if (!current.installing && !current.handedOff && current.confirmation == null) {
            checkLatest()
        }
    }

    fun requestInstall() {
        val current = state.value
        if (current.newer == null || current.verifying || current.confirmation != null ||
            current.installing || current.handedOff || !canInstall
        ) return

        mutableState.update { it.copy(verifying = true, actionError = null) }
        val check = checkLatest()
        scope.launch {
            try {
                val latest = check.await()
                if (latest != null) {
                    mutableState.update { it.copy(confirmation = latest) }
                }
            } finally {
                mutableState.update { it.copy(verifying = false) }
            }
        }
    }

    fun cancelConfirmation() {
        mutableState.update { it.copy(confirmation = null) }
    }

    fun beginInstall(): Boolean {
        val current = state.value
        if (current.confirmation == null || current.installing || current.handedOff) return false
        if (!canInstall) {
            installFailed("当前安装包无法直接更新，请前往下载页。")
            return false
        }
        mutableState.update { it.copy(confirmation = null, installing = true, actionError = null) }
        return true
    }

    fun installPreparedUpdate() {
        val updater = checkNotNull(controller)
        try {
            check(updater.canTriggerUpdateCheckUI() == AVAILABLE)
            updater.triggerUpdateCheckUI()
        } catch (failure: LinkageError) {
            throw IllegalStateException("Could not load the native installer", failure)
        }
        mutableState.update { it.copy(installing = false, handedOff = true) }
    }

    fun installFailed(message: String) {
        mutableState.update { it.copy(confirmation = null, installing = false, actionError = message) }
    }

    private fun checkLatest(): Deferred<UpdateVersion?> {
        activeCheck?.let { return it }
        mutableState.update { it.copy(checking = true, checkError = null, actionError = null) }
        val request = scope.async(start = CoroutineStart.LAZY) {
            try {
                val result = withTimeoutOrNull(10_000) { runInterruptible(Dispatchers.IO) { repository.latest() } }
                    ?: throw IOException("Update check timed out")
                val newer = result.takeIf { it.isNewerThan(repository.current) }
                mutableState.update { it.copy(checkedAt = Instant.now(), newer = newer) }
                newer
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                logger.debug("Could not check for updates", failure)
                val message = if (failure is IOException) {
                    "无法读取更新站点，请检查网络后重试。"
                } else {
                    "更新站点返回了无法识别的版本信息，请稍后重试。"
                }
                mutableState.update { it.copy(checkError = message) }
                null
            } finally {
                activeCheck = null
                mutableState.update { it.copy(checking = false) }
            }
        }
        activeCheck = request
        request.start()
        return request
    }
}
