package com.aviharez.stamp.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.aviharez.stamp.data.billing.ProStatus
import com.aviharez.stamp.data.repo.StampRepository
import com.aviharez.stamp.domain.model.Place
import com.aviharez.stamp.domain.model.Stamp
import com.aviharez.stamp.domain.usecase.Streak
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/** One stamp as a widget draws it: its picture already decoded small. */
data class WidgetStamp(
    val id: String,
    val albumId: Long,
    val day: LocalDate,
    val place: Place,
    /** Null when the picture file is missing (drawn as a blank stamp). */
    val image: Bitmap?,
)

/** Everything the three widgets show. */
data class WidgetSnapshot(
    val today: LocalDate,
    /** Newest stamp of today, null while today's stamp is still waiting. */
    val todayStamp: WidgetStamp?,
    /** Newest stamp of each of the last [PreviousDays] earlier stamped days, oldest first. */
    val previous: List<WidgetStamp>,
    val streak: Streak,
    val isPro: Boolean,
)

/** Reads the widgets' data from the same repositories as Home (same "today" rule). */
@Singleton
class WidgetData @Inject constructor(
    private val stamps: StampRepository,
    private val pro: ProStatus,
    private val clock: Clock,
) {
    fun observe(): Flow<WidgetSnapshot> = combine(
        stamps.observeRecent(RecentLimit),
        stamps.observeStreak(),
        pro.isPro.distinctUntilChanged(),
    ) { recent, streak, isPro -> snapshot(recent, streak, isPro) }
        .flowOn(Dispatchers.IO)

    private fun snapshot(recent: List<Stamp>, streak: Streak, isPro: Boolean): WidgetSnapshot {
        val today = LocalDate.now(clock)
        val newest = recent.firstOrNull()
        val todayStamp = newest?.takeIf { it.day == today }
        val previous = recent
            .filter { it.day < today }
            .distinctBy { it.day }
            .take(PreviousDays)
            .reversed()
        return WidgetSnapshot(
            today = today,
            todayStamp = todayStamp?.let(::load),
            previous = previous.map(::load),
            streak = streak,
            isPro = isPro,
        )
    }

    private fun load(stamp: Stamp) = WidgetStamp(stamp.id, stamp.albumId, stamp.day, stamp.place, decodeSmall(File(stamp.imagePath)))

    /** The rendered stamp PNG (transparent perforations, so ARGB) at about the size a widget draws it. */
    private fun decodeSmall(file: File): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val long = max(bounds.outWidth, bounds.outHeight)
        if (long <= 0) return null
        var sample = 1
        while (long / (sample * 2) >= MaxStampPx) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    companion object {
        const val PreviousDays = 2
        /** Enough rows to find the last few stamped days even after a busy day. */
        private const val RecentLimit = 40
        /** Long side of a decoded stamp; widgets draw stamps ≤ ~130dp, so this is ~3× density. */
        private const val MaxStampPx = 400
    }
}
