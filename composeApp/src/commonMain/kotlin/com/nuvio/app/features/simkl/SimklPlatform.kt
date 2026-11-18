package com.nuvio.app.features.simkl

internal expect object SimklPlatformClock {
    fun nowEpochMs(): Long
}

internal expect object SimklPkceCrypto {
    fun secureRandomBytes(size: Int): ByteArray
    fun sha256(value: ByteArray): ByteArray
}

internal interface SimklAuthStorageApi {
    fun loadMetadataPayload(): String?
    fun saveMetadataPayload(payload: String)
    fun loadAccessToken(): String?
    fun saveAccessToken(value: String?)
    fun loadRefreshToken(): String?
    fun saveRefreshToken(value: String?)
    fun loadCodeVerifier(): String?
    fun saveCodeVerifier(value: String?)
    fun removeProfile(profileId: Int)
}

internal expect object SimklAuthStorage : SimklAuthStorageApi {
    override fun loadMetadataPayload(): String?
    override fun saveMetadataPayload(payload: String)
    override fun loadAccessToken(): String?
    override fun saveAccessToken(value: String?)
    override fun loadRefreshToken(): String?
    override fun saveRefreshToken(value: String?)
    override fun loadCodeVerifier(): String?
    override fun saveCodeVerifier(value: String?)
    override fun removeProfile(profileId: Int)
}
