package com.nbttech.cardmanager

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CardViewModel(application: Application) : AndroidViewModel(application) {
    private val cardDao = CardDatabase.getDatabase(application).cardDao()
    val allCards: Flow<List<CardEntity>> = cardDao.getAllCards()

    fun insertCard(
        cardName: String,
        cardNumber: String,
        expiryDate: String,
        cvv: String,
        brand: String
    ) {
        viewModelScope.launch {
            val count = cardDao.getCardCount()
            val newCard = CardEntity(
                cardName = cardName,
                cardNumber = cardNumber,
                expiryDate = expiryDate,
                cvv = cvv,
                brand = brand,
                issuer = "",
                displayOrder = count // 末尾に追加
            )
            cardDao.insertCard(newCard)
        }
    }

    fun deleteCard(card: CardEntity) {
        viewModelScope.launch {
            cardDao.deleteCard(card)
        }
    }

    fun updateCardOrder(reorderedList: List<CardEntity>) {
        viewModelScope.launch {
            val updatedList = reorderedList.mapIndexed { index, card ->
                card.copy(displayOrder = index)
            }
            cardDao.updateCards(updatedList)
        }
    }

    suspend fun getAllCardsSync(): List<CardEntity> {
        return allCards.first()
    }

    fun importCards(cards: List<CardEntity>) {
        viewModelScope.launch {
            // IDを0にリセットして新規登録扱いにする（既存のIDとぶつからないように）
            val cardsToInsert = cards.map { it.copy(id = 0) }
            cardDao.insertCards(cardsToInsert)
        }
    }
}
