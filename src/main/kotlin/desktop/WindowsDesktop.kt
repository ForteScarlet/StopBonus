package desktop

import com.sun.jna.Native
import com.sun.jna.platform.win32.Advapi32
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.Win32Exception
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinError
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import org.slf4j.LoggerFactory
import java.awt.Window
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView

internal object WindowsDesktop {
    val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    private val logger = LoggerFactory.getLogger(WindowsDesktop::class.java)
    private val foreground by lazy { Native.load("user32", ForegroundApi::class.java) }

    fun sessionId(pid: Long): Int? {
        if (!isWindows || pid !in 1..0xffffffffL) return null
        val session = IntByReference()
        return if (Kernel32.INSTANCE.ProcessIdToSessionId(pid.toInt(), session)) session.value else null
    }

    fun allowForeground(pid: Long) {
        if (!isWindows || pid !in 1..0xffffffffL) return
        try {
            foreground.AllowSetForegroundWindow(pid.toInt())
        } catch (failure: RuntimeException) {
            logger.debug("Could not transfer foreground permission", failure)
        }
    }

    fun bringToFront(window: Window, taskbarWindow: Window) {
        if (!isWindows || !window.isShowing) return
        try {
            val handle = HWND(Native.getWindowPointer(window))
            if (!User32.INSTANCE.SetForegroundWindow(handle)) {
                val flash = WinUser.FLASHWINFO().apply {
                    hWnd = HWND(Native.getWindowPointer(taskbarWindow))
                    dwFlags = WinUser.FLASHW_TRAY
                    uCount = 3
                }
                User32.INSTANCE.FlashWindowEx(flash)
            }
        } catch (failure: RuntimeException) {
            logger.debug("Could not activate the Windows window", failure)
        }
    }

    fun ensurePrivateDirectory(directory: Path) {
        val token = WinNT.HANDLEByReference()
        if (!Advapi32.INSTANCE.OpenProcessToken(Kernel32.INSTANCE.GetCurrentProcess(), WinNT.TOKEN_QUERY, token)) {
            throw Win32Exception(Kernel32.INSTANCE.GetLastError())
        }
        val account = try {
            Advapi32Util.getTokenAccount(token.value)
        } finally {
            Kernel32.INSTANCE.CloseHandle(token.value)
        }
        if (Files.notExists(directory, NOFOLLOW_LINKS)) {
            createPrivateDirectory(directory, account.sidString)
        }

        val attributes = Kernel32.INSTANCE.GetFileAttributes(directory.toString())
        check(Files.isDirectory(directory, NOFOLLOW_LINKS) && attributes and WinNT.FILE_ATTRIBUTE_REPARSE_POINT == 0) {
            "Invalid runtime directory: $directory"
        }
        val principals = directory.fileSystem.userPrincipalLookupService
        val user = principals.lookupPrincipalByName(account.fqn)
        val system = principals.lookupPrincipalByName(Advapi32Util.getAccountBySid("S-1-5-18").fqn)
        val acl = Files.getFileAttributeView(directory, AclFileAttributeView::class.java, NOFOLLOW_LINKS)
        check(acl.owner == user && acl.acl.none { it.type() == AclEntryType.ALLOW && it.principal() !in setOf(user, system) }) {
            "Runtime directory is not private: $directory"
        }
    }

    private fun createPrivateDirectory(directory: Path, sid: String) {
        val api = Native.load("advapi32", SecurityApi::class.java, W32APIOptions.UNICODE_OPTIONS)
        val descriptor = PointerByReference()
        val sddl = "O:${sid}D:P(A;OICI;FA;;;$sid)(A;OICI;FA;;;SY)"
        if (!api.ConvertStringSecurityDescriptorToSecurityDescriptor(sddl, 1, descriptor, null)) {
            throw Win32Exception(Kernel32.INSTANCE.GetLastError())
        }
        try {
            val attributes = WinBase.SECURITY_ATTRIBUTES().apply { lpSecurityDescriptor = descriptor.value }
            val kernel = Kernel32.INSTANCE
            if (!kernel.CreateDirectory(directory.toString(), attributes)) {
                val error = kernel.GetLastError()
                if (error != WinError.ERROR_ALREADY_EXISTS) throw Win32Exception(error)
            }
        } finally {
            Kernel32.INSTANCE.LocalFree(descriptor.value)
        }
    }

    interface ForegroundApi : StdCallLibrary {
        fun AllowSetForegroundWindow(pid: Int): Boolean
    }

    interface SecurityApi : StdCallLibrary {
        fun ConvertStringSecurityDescriptorToSecurityDescriptor(
            descriptor: String,
            revision: Int,
            result: PointerByReference,
            size: IntByReference?,
        ): Boolean
    }
}
