package com.aviharez.stamp.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query(
        """
        SELECT a.*,
            (SELECT COUNT(*) FROM stamps s WHERE s.album_id = a.id) AS stamp_count,
            (SELECT s.image_path FROM stamps s WHERE s.album_id = a.id
                ORDER BY s.taken_at DESC LIMIT 1) AS latest_image_path
        FROM albums a
        ORDER BY a.created_at ASC
        """,
    )
    fun observeSummaries(): Flow<List<AlbumSummaryRow>>

    @Query("SELECT * FROM albums WHERE id = :id")
    fun observe(id: Long): Flow<AlbumEntity?>

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun get(id: Long): AlbumEntity?

    @Query("SELECT * FROM albums ORDER BY created_at ASC LIMIT 1")
    suspend fun first(): AlbumEntity?

    @Query("SELECT COUNT(*) FROM albums")
    suspend fun count(): Int

    @Insert
    suspend fun insert(album: AlbumEntity): Long

    @Update
    suspend fun update(album: AlbumEntity)
}

@Dao
interface StampDao {
    @Insert
    suspend fun insert(stamp: StampEntity)

    @Query("SELECT * FROM stamps WHERE id = :id")
    suspend fun get(id: String): StampEntity?

    @Query("SELECT * FROM stamps WHERE id = :id")
    fun observe(id: String): Flow<StampEntity?>

    @Query("SELECT * FROM stamps WHERE album_id = :albumId ORDER BY taken_at DESC")
    fun observeByAlbum(albumId: Long): Flow<List<StampEntity>>

    @Query("SELECT * FROM stamps ORDER BY taken_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<StampEntity>>

    @Query("SELECT DISTINCT day FROM stamps")
    fun observeDays(): Flow<List<String>>

    @Query("SELECT * FROM stamps WHERE day BETWEEN :from AND :to ORDER BY taken_at ASC")
    fun observeBetween(from: String, to: String): Flow<List<StampEntity>>

    /** 1-based position of a stamp in its album, oldest first ("No. 12 in …"). */
    @Query(
        """
        SELECT COUNT(*) FROM stamps
        WHERE album_id = (SELECT album_id FROM stamps WHERE id = :id)
          AND taken_at <= (SELECT taken_at FROM stamps WHERE id = :id)
        """,
    )
    fun observeNumberInAlbum(id: String): Flow<Int>

    @Query("UPDATE stamps SET caption = :caption WHERE id = :id")
    suspend fun setCaption(id: String, caption: String?)

    @Query("UPDATE stamps SET title = :title WHERE id = :id")
    suspend fun setTitle(id: String, title: String?)

    @Query("UPDATE stamps SET album_id = :albumId WHERE id = :id")
    suspend fun moveToAlbum(id: String, albumId: Long)

    @Query("DELETE FROM stamps WHERE id = :id")
    suspend fun delete(id: String)
}
