package com.example.walkon.data.repository

import com.example.walkon.data.database.WalkDao
import com.example.walkon.model.Walk
import kotlinx.coroutines.flow.Flow

// O repositório é a única porta de acesso aos dados para o ViewModel.
class WalkRepository(private val dao: WalkDao) {

    val walks: Flow<List<Walk>> = dao.getAll()

    suspend fun save(walk: Walk) = dao.insert(walk)

    suspend fun delete(walk: Walk) = dao.delete(walk)
}
