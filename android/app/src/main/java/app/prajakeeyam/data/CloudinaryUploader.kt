package app.prajakeeyam.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import app.prajakeeyam.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Downscales a picked/captured photo and uploads it straight to Cloudinary (unsigned preset). */
class CloudinaryUploader(private val context: Context, private val client: OkHttpClient) {

    val isConfigured: Boolean get() = BuildConfig.CLOUDINARY_CLOUD_NAME.isNotBlank()

    suspend fun upload(uri: Uri): String = withContext(Dispatchers.IO) {
        if (!isConfigured) throw IllegalStateException("Cloudinary is not configured")
        val jpeg = downscale(uri, MAX_SIDE, JPEG_QUALITY)
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("upload_preset", BuildConfig.CLOUDINARY_UPLOAD_PRESET)
            .addFormDataPart("file", "photo.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()))
            .build()
        val url = "https://api.cloudinary.com/v1_1/${BuildConfig.CLOUDINARY_CLOUD_NAME}/image/upload"
        client.newCall(Request.Builder().url(url).post(body).build()).execute().use { resp ->
            val text = resp.body.string()
            if (!resp.isSuccessful) throw IOException("Cloudinary upload failed: HTTP ${resp.code}")
            JSONObject(text).getString("secure_url")
        }
    }

    private fun downscale(uri: Uri, maxSide: Int, quality: Int): ByteArray {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IOException("Could not read image")
        val scale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
        if (scale < 1f) {
            bitmap = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        }
        val rotation = resolver.openInputStream(uri)?.use { exifRotation(ExifInterface(it)) } ?: 0
        if (rotation != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
    }

    private fun exifRotation(exif: ExifInterface): Int =
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

    companion object {
        private const val MAX_SIDE = 1280
        private const val JPEG_QUALITY = 80

        /** Ask Cloudinary for a resized rendition instead of downloading the full photo. */
        fun resized(url: String, width: Int): String =
            if (url.contains("/upload/")) url.replaceFirst("/upload/", "/upload/w_$width,c_limit,q_auto,f_auto/") else url
    }
}
