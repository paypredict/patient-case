package net.paypredict.patient.cases.data.cases

import net.paypredict.patient.cases.data.worklist.ordersDir
import net.paypredict.patient.cases.digest
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter


/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 11/23/2018.
 */

val casesArchiveDir: File by lazy {
    ordersDir.resolve("archive").apply { mkdir() }
}

val srcCasesArchiveDir: File by lazy {
    ordersDir.resolve("archive-src").apply { mkdir() }
}

val outCasesArchiveDir: File by lazy {
    ordersDir.resolve("archive-out").apply { mkdir() }
}

fun casesArchiveFile(digest: String): File =
    casesArchiveDir
        .resolve(digest.take(4)).apply { mkdir() }
        .resolve(digest)

fun File.archiveCaseFile(
    digest: String = digest(),
    backupMode: BackupMode = BackupMode.NONE
): String {
    val archiveFile = casesArchiveFile(digest)
    if (!archiveFile.exists())
        copyToWithCreationTime(archiveFile, backupMode.toCreationTime(this))
    backup(backupMode)

    return digest
}

sealed class BackupMode {
    object NONE : BackupMode()
    object SRC : BackupMode()
    class OUT(val creationTime: FileTime) : BackupMode()
}

private fun File.backup(backupMode: BackupMode) {
    val creationTime =
        backupMode.toCreationTime(this)

    val backupDir =
        when (backupMode) {
            BackupMode.NONE -> null
            BackupMode.SRC -> srcCasesArchiveDir
            is BackupMode.OUT -> outCasesArchiveDir
        }
            ?.resolve(creationTime.formatAsDateSafeFileName(this))
            ?.apply { mkdir() }
            ?: return

    val backupFile = backupDir.resolve(name)
    if (!backupFile.exists()) {
        copyToWithCreationTime(backupFile, creationTime)
    }
}

private fun BackupMode.toCreationTime(file: File): FileTime? =
    when (this) {
        BackupMode.NONE -> null
        BackupMode.SRC -> file.readCreationTime()
        is BackupMode.OUT -> creationTime
    }

private fun FileTime?.formatAsDateSafeFileName(file: File): String =
    (this ?: file.toPath().readCreationTime())
        .toInstant()
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ISO_LOCAL_DATE)

private fun File.copyToWithCreationTime(target: File, creationTime: FileTime? = null) {
    val path = toPath()
    val targetPath = target.toPath()
    Files.copy(path, targetPath)
    Files.setAttribute(
        targetPath, "creationTime", creationTime ?: path.readCreationTime()
    )
}

fun File.readCreationTime(): FileTime =
    toPath().readCreationTime()

private fun Path.readCreationTime(): FileTime =
    Files
        .readAttributes(this, BasicFileAttributes::class.java)
        .creationTime()

