package com.nbttech.cardmanager

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    // displayOrder 順にカードを取得
    @Query("SELECT * FROM cards ORDER BY displayOrder ASC")
    fun getAllCards(): Flow<List<CardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: CardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(cards: List<CardEntity>)

    @Delete
    suspend fun deleteCard(card: CardEntity)

    // 並び替え後にリスト全体を更新するため
    @Update
    suspend fun updateCards(cards: List<CardEntity>)

    // 新しいカードの displayOrder を決めるために使用
    @Query("SELECT COUNT(*) FROM cards")
    suspend fun getCardCount(): Int
}
