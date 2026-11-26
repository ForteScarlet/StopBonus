package view.account.record

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.openFileWithDefaultApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File

internal object ExportFileActions {
    private const val WINDOWS_OS_NAME = "windows"
    private const val WINDOWS_EXPLORER = "explorer.exe"
    private const val WINDOWS_SELECT_OPTION = "/select,"

    suspend fun openFile(file: PlatformFile): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { FileKit.openFileWithDefaultApplication(file) }
    }

    suspend fun openDirectory(file: PlatformFile): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val selectedFile = File(file.absolutePath()).absoluteFile
            val directory = selectedFile.parentFile ?: selectedFile
            val os = System.getProperty("os.name").orEmpty()
            when {
                os.contains(WINDOWS_OS_NAME, ignoreCase = true) -> {
                    ProcessBuilder(WINDOWS_EXPLORER, WINDOWS_SELECT_OPTION + selectedFile.path).start()
                    Unit
                }
                os.equals("Linux", ignoreCase = true) ->
                    FileKit.openFileWithDefaultApplication(PlatformFile(directory.path))
                else -> openWithDesktop(directory)
            }
        }
    }

    private fun openWithDesktop(file: File) {
        check(Desktop.isDesktopSupported()) { "系统不支持打开文件夹" }
        Desktop.getDesktop().open(file)
    }
}
