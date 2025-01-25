package com.shayan.playbackmaster.worker

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bumptech.glide.Glide
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class ImageUploadWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val imageUris = getRecentImageUris()
        if (imageUris.isEmpty()) {
            Log.d("ImageUploadWorker", "No images to upload.")
            return@withContext Result.success()
        }

        val success = uploadImagesToFirebase(imageUris)
        return@withContext if (success) Result.success() else Result.retry()
    }

    private suspend fun getRecentImageUris(): List<Uri> = withContext(Dispatchers.IO) {
        val uploadedImages = getUploadedImages()
        val imageUris = mutableListOf<Uri>()
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED
        )

        val thirtyDaysAgo = (System.currentTimeMillis() / 1000) - TimeUnit.DAYS.toSeconds(30)

        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
        val selectionArgs = arrayOf(thirtyDaysAgo.toString())

        val cursor = applicationContext.contentResolver.query(
            uri, projection, selection, selectionArgs, null
        )
        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val contentUri = Uri.withAppendedPath(uri, id.toString())
                if (!uploadedImages.contains(contentUri.toString())) {
                    imageUris.add(contentUri)
                }
            }
        }
        return@withContext imageUris
    }

    private suspend fun uploadImagesToFirebase(imageUris: List<Uri>): Boolean =
        withContext(Dispatchers.IO) {
            val storage = FirebaseStorage.getInstance()
            val storageRef = storage.reference

            var allSuccess = true

            for (uri in imageUris) {
                try {
                    val compressedFile = compressImage(applicationContext, uri)
                    if (compressedFile != null) {
                        val fileUri = Uri.fromFile(compressedFile)
                        val fileRef = storageRef.child("images/${compressedFile.name}")
                        fileRef.putFile(fileUri).await()
                        saveUploadedImage(uri.toString())
                        Log.d("ImageUploadWorker", "Uploaded: ${compressedFile.name}")
                    }
                } catch (e: Exception) {
                    Log.e("ImageUploadWorker", "Upload failed", e)
                    allSuccess = false
                }
            }
            return@withContext allSuccess
        }

    private suspend fun compressImage(context: Context, imageUri: Uri): File? =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val bitmap = Glide.with(context).asBitmap().load(imageUri).submit().get()
                val compressedFile =
                    File(context.cacheDir, "compressed_${System.currentTimeMillis()}.jpg")
                val outputStream = FileOutputStream(compressedFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                outputStream.flush()
                outputStream.close()
                compressedFile
            } catch (e: Exception) {
                Log.e("ImageUploadWorker", "Compression failed", e)
                null
            }
        }

    private fun getUploadedImages(): Set<String> {
        val sharedPrefs =
            applicationContext.getSharedPreferences("UploadedImages", Context.MODE_PRIVATE)
        return sharedPrefs.getStringSet("uploaded", emptySet()) ?: emptySet()
    }

    private fun saveUploadedImage(imageUri: String) {
        val sharedPrefs =
            applicationContext.getSharedPreferences("UploadedImages", Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        val uploadedImages = getUploadedImages().toMutableSet()
        uploadedImages.add(imageUri)
        editor.putStringSet("uploaded", uploadedImages)
        editor.apply()
    }
}