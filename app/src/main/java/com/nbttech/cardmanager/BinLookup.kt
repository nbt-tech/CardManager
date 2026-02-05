package com.nbttech.cardmanager

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BinResponse(val bank: Bank? = null) {
    @Serializable
    data class Bank(val name: String? = null)
}

object BinLookup {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    suspend fun getIssuer(cardNumber: String): String {
        // BINは通常先頭6〜8桁
        if (cardNumber.length < 6) return ""
        return try {
            val bin = cardNumber.take(8)
            val response: BinResponse = client.get("https://lookup.binlist.net/$bin").body()
            response.bank?.name ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
