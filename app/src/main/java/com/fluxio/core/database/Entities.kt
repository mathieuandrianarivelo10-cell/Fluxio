package com.fluxio.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_channels", primaryKeys = ["channelId", "userEmail"])
data class FavoriteChannelEntity(
    val channelId: String,
    val userEmail: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "channel_comments")
data class ChannelCommentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String,
    val userEmail: String,
    val commentText: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_programs")
data class CustomProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String,
    val programName: String,
    val startTime: String,
    val endTime: String,
    val day: String,
    val description: String,
    val imageUrl: String = ""
)
