package view.account.record

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.openFileWithDefaultApplication
import java.awt.Desktop
import java.io.File

internal object ExportFileActions {
    private const val WINDOWS_OS_NAME = "windows"
    private const val WINDOWS_EXPLORER = "explorer.exe"
    private const val WINDOWS_SELECT_OPTION = "/select,"

    fun openFile(file: PlatformFile): Result<Unit> = runCatching {
        FileKit.openFileWithDefaultApplication(file)
    }

    fun openDirectory(file: PlatformFile): Result<Unit> = runCatching {
        val selectedFile = File(file.absolutePath()).absoluteFile
        if (isWindows()) {
            ProcessBuilder(WINDOWS_EXPLORER, WINDOWS_SELECT_OPTION + selectedFile.path).start()
        } else {
            openWithDesktop(selectedFile.parentFile ?: selectedFile)
        }
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().contains(WINDOWS_OS_NAME, ignoreCase = true)

    private fun openWithDesktop(file: File) {
        check(Desktop.isDesktopSupported()) { "系统不支持打开文件夹" }
        Desktop.getDesktop().open(file)
    }
}
