package com.aviharez.stamp.data.billing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aviharez.stamp.domain.model.PerforationStyle
import com.aviharez.stamp.domain.model.StampLook
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Which looks a user may save right now: Pro, plus Pro edges unlocked for a day by a short ad. */
data class LookAccess(val isPro: Boolean = false, val trialEdges: Set<PerforationStyle> = emptySet()) {
    fun canUse(edge: PerforationStyle): Boolean = edge.free || isPro || edge in trialEdges
    fun canUse(look: StampLook): Boolean = (look.filter.free || isPro) && canUse(look.edge)
}

/**
 * Edge styles: who may use which (Classic is free, the rest Pro or a 24-hour trial earned with a
 * rewarded ad, CLAUDE.md §4), and the edge new stamps start with (the last one the user picked).
 * Trials are per edge and are not in backups (like Pro, they belong to this phone's DataStore).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class EdgeAccess @Inject constructor(
    private val store: DataStore<Preferences>,
    private val proStatus: ProStatus,
    private val clock: Clock,
) {
    private val trialEnds: Flow<Map<PerforationStyle, Long>> =
        store.data.map { decode(it[TrialEnds]) }.distinctUntilChanged()

    /** Edges unlocked by an ad right now; each one drops out by itself when its day is over. */
    val trialEdges: Flow<Set<PerforationStyle>> = trialEnds.flatMapLatest { ends ->
        flow {
            while (true) {
                val now = clock.millis()
                val live = ends.filterValues { it > now }
                emit(live.keys)
                val next = live.values.minOrNull() ?: break
                delay(next - now)
            }
        }
    }.distinctUntilChanged()

    val access: Flow<LookAccess> =
        combine(proStatus.isPro, trialEdges) { pro, trial -> LookAccess(pro, trial) }.distinctUntilChanged()

    /** The edge the camera and gallery import frame new stamps with. */
    val edgeForNewStamps: Flow<PerforationStyle> = combine(
        store.data.map { prefs -> PerforationStyle.entries.firstOrNull { it.name == prefs[LastEdge] } ?: PerforationStyle.Classic },
        access,
    ) { last, access -> if (access.canUse(last)) last else PerforationStyle.Classic }.distinctUntilChanged()

    suspend fun setLastEdge(edge: PerforationStyle) {
        store.edit { it[LastEdge] = edge.name }
    }

    /** A rewarded ad was watched with [edge] on show: it is free to use for [ProRules.EdgeTrialHours]. */
    suspend fun startTrial(edge: PerforationStyle) {
        if (edge.free) return
        val now = clock.millis()
        store.edit { prefs ->
            val ends = decode(prefs[TrialEnds]).filterValues { it > now } +
                (edge to now + TimeUnit.HOURS.toMillis(ProRules.EdgeTrialHours))
            prefs[TrialEnds] = encode(ends)
        }
    }

    private companion object {
        val LastEdge = stringPreferencesKey("last_edge")
        /** "Bold:1730000000000,Scallop:…" (edge name : end, epoch millis). */
        val TrialEnds = stringPreferencesKey("edge_trial_ends")

        fun decode(text: String?): Map<PerforationStyle, Long> = text.orEmpty().split(',').mapNotNull { part ->
            val name = part.substringBefore(':')
            val edge = PerforationStyle.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
            val end = part.substringAfter(':', "").toLongOrNull() ?: return@mapNotNull null
            edge to end
        }.toMap()

        fun encode(ends: Map<PerforationStyle, Long>): String =
            ends.entries.joinToString(",") { (edge, end) -> "${edge.name}:$end" }
    }
}
