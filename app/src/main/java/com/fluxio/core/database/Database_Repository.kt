package com.fluxio.core.database

import android.content.Context
import kotlinx.coroutines.flow.Flow

class DatabaseRepository(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val favoriteChannelDao = db.favoriteChannelDao()
    private val channelCommentDao = db.channelCommentDao()
    private val customProgramDao = db.customProgramDao()

    // Favorites
    fun getFavorites(userEmail: String): Flow<List<FavoriteChannelEntity>> {
        return favoriteChannelDao.getFavorites(userEmail)
    }

    suspend fun insertFavorite(favorite: FavoriteChannelEntity) {
        favoriteChannelDao.insertFavorite(favorite)
    }

    suspend fun deleteFavorite(channelId: String, userEmail: String) {
        favoriteChannelDao.deleteFavorite(channelId, userEmail)
    }

    suspend fun isFavorite(channelId: String, userEmail: String): Boolean {
        return favoriteChannelDao.isFavorite(channelId, userEmail)
    }

    // Comments
    fun getComments(channelId: String): Flow<List<ChannelCommentEntity>> {
        return channelCommentDao.getComments(channelId)
    }

    suspend fun insertComment(comment: ChannelCommentEntity) {
        channelCommentDao.insertComment(comment)
    }

    suspend fun deleteComment(id: Long) {
        channelCommentDao.deleteComment(id)
    }

    // Custom Programs
    fun getAllPrograms(): Flow<List<CustomProgramEntity>> {
        return customProgramDao.getAllPrograms()
    }

    suspend fun insertProgram(program: CustomProgramEntity): Long {
        return customProgramDao.insertProgram(program)
    }

    suspend fun deleteProgram(id: Long) {
        customProgramDao.deleteProgram(id)
    }

    suspend fun deleteProgramByName(channelId: String, programName: String) {
        customProgramDao.deleteProgramByName(channelId, programName)
    }
}
