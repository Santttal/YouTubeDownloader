package com.santttal.youtubedownloader.util

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.Locale

class NewPipeDownloader(
    private val client: OkHttpClient
) : Downloader() {

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder().url(request.url())
        val seenHeaders = mutableSetOf<String>()

        request.headers()?.forEach { (name, values) ->
            values.forEach { value ->
                builder.addHeader(name, value)
            }
            seenHeaders += name.lowercase(Locale.US)
        }

        Request.getHeadersFromLocalization(request.localization()).forEach { (name, values) ->
            if (name.lowercase(Locale.US) !in seenHeaders) {
                values.forEach { value ->
                    builder.addHeader(name, value)
                }
            }
        }

        val requestBody = when (request.httpMethod()) {
            "POST", "PUT", "PATCH", "DELETE" ->
                (request.dataToSend() ?: ByteArray(0)).toRequestBody(null)
            else -> null
        }

        // YouTube consent cookie — prevents "The page needs to be reloaded" error
        if (request.url().contains("youtube.com") || request.url().contains("googlevideo.com")) {
            if ("cookie" !in seenHeaders) {
                builder.addHeader("Cookie", "SOCS=CAISNQgDEitib3FfaWRlbnRpdHlmcm9udGVuZHVpc2VydmVyXzIwMjMwODI5LjA3X3AxGgJlbiACGgYIgJnsBhAB")
            }
        }

        val okHttpRequest = builder
            .method(request.httpMethod(), requestBody)
            .build()

        client.newCall(okHttpRequest).execute().use { response ->
            val latestUrl = response.request.url.toString()

            if (response.code == 429 || latestUrl.contains("sorry", ignoreCase = true)) {
                throw ReCaptchaException("Rate-limited or CAPTCHA response", latestUrl)
            }

            return Response(
                response.code,
                response.message,
                response.headers.toMultimap().mapValues { it.value.toList() },
                response.body?.string(),
                latestUrl
            )
        }
    }
}
