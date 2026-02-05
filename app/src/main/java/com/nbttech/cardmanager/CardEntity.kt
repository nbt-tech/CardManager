package com.nbttech.cardmanager

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cardName: String,
    val cardNumber: String,
    val expiryDate: String,
    val cvv: String,
    val brand: String,
    val issuer: String,
    val displayOrder: Int = 0 // 並び順保存用
)
