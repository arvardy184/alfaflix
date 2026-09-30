package com.application.alfaflix.data.remote.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the TMDB API Read Access Token to every API request as a bearer token.
 *
 * Header auth keeps the credential out of URLs, so it cannot leak through logs, the HTTP
 * cache or crash reports the way a `?api_key=` query parameter can.
 *
 * This interceptor belongs to the API client only. Poster and backdrop images come from
 * TMDB's CDN and are fetched by Coil's own client, so the token is never sent there.
 */
class TmdbAuthInterceptor(private val accessToken: String) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .build()
        return chain.proceed(request)
    }
}
