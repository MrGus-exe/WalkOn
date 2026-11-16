package com.example.walkon.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.example.walkon.model.Walk
import kotlinx.coroutines.flow.Flow

// DAO = comandos SQL que o app usa no banco.
@Dao
interface WalkDao {

    @Insert
    suspend fun insert(walk: Walk)

    @Delete
    suspend fun delete(walk: Walk)

    // Flow: a lista é atualizada automaticamente quando o banco muda.
    @Query("SELECT * FROM walks ORDER BY date DESC")
    fun getAll(): Flow<List<Walk>>
}
