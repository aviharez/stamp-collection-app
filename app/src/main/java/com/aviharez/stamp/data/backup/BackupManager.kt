package com.aviharez.stamp.data.backup

import android.content.Context
import android.net.Uri
import com.aviharez.stamp.data.billing.ProStatus
import com.aviharez.stamp.data.db.AlbumDao
import com.aviharez.stamp.data.db.AlbumEntity
import com.aviharez.stamp.data.db.ScrapPageDao
import com.aviharez.stamp.data.db.StampDao
import com.aviharez.stamp.data.db.StampEntity
import com.aviharez.stamp.data.image.RenderSpec
import com.aviharez.stamp.data.image.StampStorage
import com.aviharez.stamp.data.settings.SettingsRepository
import com.aviharez.stamp.di.AppScope
import com.aviharez.stamp.domain.model.StampFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Clock
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.aviharez.stamp.data.analytics.UsageStats
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException

enum class BackupError { NotABackup, NewerVersion, Failed }

sealed interface BackupState {
    data object Idle : BackupState

    /** [total] = 0 while the count isn't known yet (reading the file). */
    data class Working(val restoring: Boolean, val done: Int = 0, val total: Int = 0) : BackupState

    data class BackedUp(val stamps: Int) : BackupState

    data class Restored(val stamps: Int, val albums: Int, val alreadyHere: Int) : BackupState

    data class Failed(val restoring: Boolean, val error: BackupError) : BackupState
}

/**
 * Backup / restore to one file the user picks (CLAUDE.md §3: works fully offline). Runs in the app
 * scope so it finishes if the user leaves Profile. See [BackupFormat] for the file and the merge rules.
 */
@Singleton
class BackupManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val albums: AlbumDao,
    private val stamps: StampDao,
    private val scrapPages: ScrapPageDao,
    private val storage: StampStorage,
    private val settings: SettingsRepository,
    private val pro: ProStatus,
    private val clock: Clock,
    @param:AppScope private val scope: CoroutineScope,
    private val usage: UsageStats,
) {
    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state.asStateFlow()

    val lastBackupAt = settings.lastBackupAt

    /** Suggested file name, e.g. "Postmory backup 2026-11-04.zip". */
    fun suggestedName(appName: String): String = "$appName backup ${java.time.LocalDate.now(clock)}.zip"

    fun backUp(to: Uri) = run(restoring = false) { write(to) }

    fun restore(from: Uri) = run(restoring = true) { read(from) }

    /** The result has been shown; go back to the plain buttons. */
    fun dismiss() {
        if (_state.value !is BackupState.Working) _state.value = BackupState.Idle
    }

    private fun run(restoring: Boolean, block: suspend () -> BackupState) {
        if (_state.value is BackupState.Working) return
        _state.value = BackupState.Working(restoring)
        scope.launch(Dispatchers.IO) {
            _state.value = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val notOurs = e is SerializationException || e is ZipException || e is IllegalArgumentException
                BackupState.Failed(restoring, if (notOurs) BackupError.NotABackup else BackupError.Failed)
            }
            usage.backup(restoring, ok = _state.value !is BackupState.Failed)
        }
    }

    private suspend fun write(to: Uri): BackupState {
        val now = clock.millis()
        val all = stamps.all().filter { File(it.imagePath).isFile } // a stamp without its picture can't come back
        val manifest = BackupFormat.manifestOf(
            albums = albums.all(),
            stamps = all,
            scrapPages = scrapPages.all(),
            originalExtension = { s -> File(s.originalPath).takeIf { it.isFile }?.extension?.takeIf(SafeExtension::matches) },
            settings = BackupSettings(mirrorSelfie = settings.mirrorSelfie.first(), bonusAlbums = pro.bonusAlbums.first()),
            now = now,
        )
        val byId = all.associateBy(StampEntity::id)
        val out = context.contentResolver.openOutputStream(to, "w") ?: throw IOException("no output")
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(BackupFormat.ManifestEntry))
            zip.write(BackupFormat.json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
            zip.closeEntry()
            zip.setLevel(Deflater.NO_COMPRESSION) // PNG/JPEG are compressed already; this keeps it fast
            manifest.stamps.forEachIndexed { i, b ->
                val s = byId.getValue(b.id)
                zip.addFile(b.image, File(s.imagePath))
                b.original?.let { zip.addFile(it, File(s.originalPath)) }
                _state.value = BackupState.Working(restoring = false, done = i + 1, total = manifest.stamps.size)
            }
        }
        settings.setLastBackupAt(now)
        return BackupState.BackedUp(manifest.stamps.size)
    }

    private suspend fun read(from: Uri): BackupState {
        val staging = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        try {
            var manifest: BackupManifest? = null
            val staged = HashSet<String>()
            val input = context.contentResolver.openInputStream(from) ?: throw IOException("no input")
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when {
                        entry.isDirectory -> Unit
                        entry.name == BackupFormat.ManifestEntry ->
                            manifest = BackupFormat.json.decodeFromString(BackupManifest.serializer(), zip.readText(MaxManifestBytes))
                        BackupFormat.isImageEntry(entry.name) && entry.name !in staged -> {
                            stagedFile(staging, entry.name).outputStream().use { zip.copyTo(it) }
                            staged += entry.name
                        }
                    }
                }
            }
            val m = manifest ?: return BackupState.Failed(restoring = true, error = BackupError.NotABackup)
            if (m.format > BackupFormat.Version) return BackupState.Failed(restoring = true, error = BackupError.NewerVersion)

            val plan = BackupFormat.plan(m, albums.all(), stamps.ids().toSet(), staged, scrapPages.ids().mapTo(HashSet()) { it.lowercase() })
            val albumIds = plan.reuseAlbums.toMutableMap()
            plan.newAlbums.forEach { a ->
                albumIds[a.key] = albums.insert(AlbumEntity(name = a.name, category = a.category, cover = a.cover, createdAt = a.createdAt))
            }
            plan.newStamps.forEachIndexed { i, b ->
                val rendered = storage.renderedFile(b.id)
                stagedFile(staging, b.image).moveTo(rendered)
                val originalEntry = b.original?.takeIf { it in staged && it.startsWith("${BackupFormat.OriginalsDir}/${b.id}.") }
                val original = storage.originalFile(b.id, originalEntry?.let(BackupFormat::extensionOf) ?: "jpg")
                originalEntry?.let { stagedFile(staging, it).moveTo(original) }
                stamps.insert(b.toEntity(albumIds.getValue(b.albumKey), rendered, original))
                _state.value = BackupState.Working(restoring = true, done = i + 1, total = plan.newStamps.size)
            }
            plan.newScrapPages.forEach { scrapPages.upsert(BackupFormat.scrapPageEntity(it, albumIds.getValue(it.albumKey))) }
            m.settings.mirrorSelfie?.let { settings.setMirrorSelfie(it) }
            pro.restoreBonusAlbums(m.settings.bonusAlbums)
            return BackupState.Restored(plan.newStamps.size, plan.newAlbums.size, plan.alreadyHere)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun stagedFile(staging: File, entry: String) = File(staging, entry.replace('/', '_'))

    private companion object {
        val SafeExtension = Regex("^[A-Za-z0-9]{1,5}$")
        const val MaxManifestBytes = 32 * 1024 * 1024
    }
}

private fun ZipOutputStream.addFile(name: String, file: File) {
    putNextEntry(ZipEntry(name).apply { time = file.lastModified() })
    file.inputStream().use { it.copyTo(this) }
    closeEntry()
}

private fun InputStream.readText(limit: Int): String {
    val bytes = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val n = read(buffer)
        if (n < 0) break
        bytes.write(buffer, 0, n)
        if (bytes.size() > limit) throw IllegalArgumentException("manifest too large")
    }
    return bytes.toString(Charsets.UTF_8.name())
}

/** Rename within app storage (same disk), copying if that isn't possible. */
private fun File.moveTo(target: File) {
    target.parentFile?.mkdirs()
    if (!renameTo(target)) {
        copyTo(target, overwrite = true)
        delete()
    }
}

private fun BackupStamp.toEntity(albumId: Long, rendered: File, original: File) = StampEntity(
    id = id,
    albumId = albumId,
    shape = shape,
    perforation = perforation,
    imagePath = rendered.path,
    originalPath = original.path,
    takenAt = takenAt,
    day = day,
    createdDay = createdDay,
    city = city,
    country = country,
    countryCode = countryCode,
    latitude = latitude,
    longitude = longitude,
    title = title,
    caption = caption,
    source = source,
    filter = StampFilter.fromName(filter).name,
    renderSpec = RenderSpec.decode(renderSpec)?.encode(),
)
