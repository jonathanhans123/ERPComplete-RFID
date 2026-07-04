package com.erpcomplete.rfid.rfid

import android.content.Context
import com.erpcomplete.rfid.R
import com.erpcomplete.rfid.data.remote.ErpApiService
import com.erpcomplete.rfid.data.remote.FirmwareCheckResult
import com.erpcomplete.rfid.util.ApiErrorParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class ZebraFirmwareRepository(
    private val appContext: Context,
    private val api: ErpApiService,
    private val downloadClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .build(),
) {

    suspend fun checkForUpdate(model: String, currentVersion: String?, refresh: Boolean = false): FirmwareCheckResult {
        val res = api.checkFirmwareUpdate(model, currentVersion, if (refresh) 1 else 0)
        if (!res.isSuccessful) error(ApiErrorParser.httpMessage(res))
        val body = res.body()?.data ?: error(appContext.getString(R.string.error_empty_firmware_check))
        return body
    }

    suspend fun downloadFirmware(
        downloadUrl: String,
        targetFile: File,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(downloadUrl).get().build()
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error(appContext.getString(R.string.error_firmware_download_http, response.code))
            }
            val body = response.body ?: error(appContext.getString(R.string.error_empty_firmware_download_body))
            val total = body.contentLength().coerceAtLeast(0L)
            targetFile.parentFile?.mkdirs()
            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0L) {
                            onProgress(((downloaded * 100L) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
        }
        onProgress(100)
        targetFile
    }
}
