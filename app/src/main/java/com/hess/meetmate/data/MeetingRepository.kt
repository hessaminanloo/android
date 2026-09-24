package com.hess.meetmate.data

import kotlinx.coroutines.flow.Flow

class MeetingRepository(private val dao: MeetingDao) {
    fun observeMeetings(): Flow<List<MeetingEntity>> = dao.observeAll()
    suspend fun save(meeting: MeetingEntity) = dao.upsert(meeting)
    suspend fun get(id: Long) = dao.get(id)
}
