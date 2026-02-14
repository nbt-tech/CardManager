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
            // 保存前にイシュアを取得
            val issuer = BinLookup.getIssuer(cardNumber)
            
            val count = cardDao.getCardCount()
            val newCard = CardEntity(
                cardName = cardName,
                cardNumber = cardNumber,
                expiryDate = expiryDate,
                cvv = cvv,
                brand = brand,
                issuer = issuer, // 取得した値をセット
                displayOrder = count
            )
            cardDao.insertCard(newCard)
        }
    }

    fun updateCard(card: CardEntity) {
        viewModelScope.launch {
            // カード番号が変わっている可能性があるのでイシュアを再取得
            val issuer = BinLookup.getIssuer(card.cardNumber)
            val updatedCard = card.copy(issuer = issuer)
            cardDao.insertCard(updatedCard) // OnConflictStrategy.REPLACE is used in Dao
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
            val cardsToInsert = cards.map { it.copy(id = 0) }
            cardDao.insertCards(cardsToInsert)
        }
    }

    suspend fun getCardById(id: Int): CardEntity? {
        return allCards.first().find { it.id == id }
    }
}
