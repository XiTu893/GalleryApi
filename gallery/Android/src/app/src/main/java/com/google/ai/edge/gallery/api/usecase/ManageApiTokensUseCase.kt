package com.google.ai.edge.gallery.api.usecase

import com.google.ai.edge.gallery.api.TokenManager
import com.google.ai.edge.gallery.api.model.ApiToken
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ManageApiTokensUseCase @Inject constructor(
    private val tokenManager: TokenManager,
) {
    fun generateToken(name: String, expiresInDays: Int? = null): ApiToken {
        return tokenManager.generateToken(name, expiresInDays)
    }

    fun listTokens(): List<ApiToken> {
        return tokenManager.listTokens()
    }

    fun deleteToken(tokenValue: String): Boolean {
        return tokenManager.deleteToken(tokenValue)
    }

    fun validateToken(tokenValue: String): Boolean {
        return tokenManager.validateToken(tokenValue)
    }
}
