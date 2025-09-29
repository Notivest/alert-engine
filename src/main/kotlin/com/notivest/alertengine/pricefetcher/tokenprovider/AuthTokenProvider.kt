package com.notivest.alertengine.pricefetcher.tokenprovider

interface AuthTokenProvider { suspend fun getToken(): String? }