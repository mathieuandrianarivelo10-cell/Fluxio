package com.fluxio.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteChannelDao {
    @Query("SELECT * FROM favorite_channels WHERE userEmail = :userEmail")
    fun getFavorites(userEmail: String): Flow<List<FavoriteChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteChannelEntity)

    @Query("DELETE FROM favorite_channels WHERE channelId = :channelId AND userEmail = :userEmail")
    suspend fun deleteFavorite(channelId: String, userEmail: String)

    @Query("SELECT COUNT(*) > 0 FROM favorite_channels WHERE channelId = :channelId AND userEmail = :userEmail")
    suspend fun isFavorite(channelId: String, userEmail: String): Boolean
}

@Dao
interface ChannelCommentDao {
    @Query("SELECT * FROM channel_comments WHERE channelId = :channelId ORDER BY timestamp DESC")
    fun getComments(channelId: String): Flow<List<ChannelCommentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: ChannelCommentEntity)

    @Query("DELETE FROM channel_comments WHERE id = :id")
    suspend fun deleteComment(id: Long)
}

@Dao
interface CustomProgramDao {
    @Query("SELECT * FROM custom_programs ORDER BY id DESC")
    fun getAllPrograms(): Flow<List<CustomProgramEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgram(program: CustomProgramEntity): Long

    @Query("DELETE FROM custom_programs WHERE id = :id")
    suspend fun deleteProgram(id: Long)

    @Query("DELETE FROM custom_programs WHERE channelId = :channelId AND programName = :programName")
    suspend fun deleteProgramByName(channelId: String, programName: String)
}
