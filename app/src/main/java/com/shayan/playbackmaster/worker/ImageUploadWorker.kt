package com.shayan.playbackmaster.worker

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
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
        // Get images from the last 30 days
        val imagePaths = getRecentImagePaths()
        if (imagePaths.isEmpty()) {
            Log.d("ImageUploadWorker", "No new images found to upload.")
            return@withContext Result.success()
        }

        // Upload images
        val success = uploadImagesToFirebase(imagePaths)
        return@withContext if (success) {
            Result.success()
        } else {
            Result.retry() // Retry only if there is a transient failure
        }
    }

    private suspend fun getRecentImagePaths(): List<String> = withContext(Dispatchers.IO) {
        val uploadedImages = getUploadedImages() // Retrieve already-uploaded images
        val imagePaths = mutableListOf<String>()
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media.DATA, MediaStore.Images.Media.DATE_ADDED
        )

        // Calculate timestamp for 30 days ago
        val thirtyDaysAgo = (System.currentTimeMillis() / 1000) - TimeUnit.DAYS.toSeconds(30)

        // Query to fetch images added in the last 30 days
        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
        val selectionArgs = arrayOf(thirtyDaysAgo.toString())

        val cursor = applicationContext.contentResolver.query(
            uri, projection, selection, selectionArgs, null
        )
        cursor?.use {
            val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            while (it.moveToNext()) {
                val filePath = it.getString(columnIndex)
                if (!uploadedImages.contains(filePath)) {
                    imagePaths.add(filePath)
                }
            }
        }
        return@withContext imagePaths
    }

    private suspend fun uploadImagesToFirebase(imagePaths: List<String>): Boolean =
        withContext(Dispatchers.IO) {
            val storage = FirebaseStorage.getInstance()
            val storageRef = storage.reference

            var allSuccess = true

            for (path in imagePaths) {
                try {
                    // Compress the image before uploading
                    val compressedFile = compressImage(applicationContext, path)
                    if (compressedFile != null) {
                        val fileUri = Uri.fromFile(compressedFile)
                        val fileRef = storageRef.child("images/${compressedFile.name}")

                        // Upload file and wait for completion
                        fileRef.putFile(fileUri).await()

                        // Save uploaded image to prevent re-uploading
                        saveUploadedImage(path)
                        Log.d("ImageUploadWorker", "Successfully uploaded: ${compressedFile.name}")
                    }
                } catch (e: Exception) {
                    Log.e("ImageUploadWorker", "Error during upload", e)
                    allSuccess = false
                }
            }
            return@withContext allSuccess
        }

    private suspend fun compressImage(context: Context, imagePath: String): File? =
        withContext(Dispatchers.IO) {
            return@withContext try {
                // Load the image as a Bitmap using Glide
                val bitmap = Glide.with(context).asBitmap().load(imagePath).submit().get()

                // Create a temporary file to save the compressed image
                val compressedFile =
                    File(context.cacheDir, "compressed_${System.currentTimeMillis()}.jpg")
                val outputStream = FileOutputStream(compressedFile)

                // Compress the Bitmap to JPEG with 80% quality
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                outputStream.flush()
                outputStream.close()

                compressedFile
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    private fun getUploadedImages(): Set<String> {
        val sharedPrefs =
            applicationContext.getSharedPreferences("UploadedImages", Context.MODE_PRIVATE)
        return sharedPrefs.getStringSet("uploaded", emptySet()) ?: emptySet()
    }

    private fun saveUploadedImage(imagePath: String) {
        val sharedPrefs =
            applicationContext.getSharedPreferences("UploadedImages", Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        val uploadedImages = getUploadedImages().toMutableSet()
        uploadedImages.add(imagePath)
        editor.putStringSet("uploaded", uploadedImages)
        editor.apply()
    }

    companion object {
        fun enqueueWork(context: Context) {
            val imagePaths = getRecentImages(context)
            if (imagePaths.isNotEmpty()) {
                val workRequest = OneTimeWorkRequestBuilder<ImageUploadWorker>().build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    "ImageUploadWorker", ExistingWorkPolicy.REPLACE, workRequest
                )
            }
        }

        private fun getRecentImages(context: Context): List<String> {
            val uploadedImages =
                context.getSharedPreferences("UploadedImages", Context.MODE_PRIVATE)
                    .getStringSet("uploaded", emptySet()) ?: emptySet()

            val imagePaths = mutableListOf<String>()
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Images.Media.DATA, MediaStore.Images.Media.DATE_ADDED
            )

            val thirtyDaysAgo = (System.currentTimeMillis() / 1000) - TimeUnit.DAYS.toSeconds(30)
            val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
            val selectionArgs = arrayOf(thirtyDaysAgo.toString())

            val cursor = context.contentResolver.query(
                uri, projection, selection, selectionArgs, null
            )
            cursor?.use {
                val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                while (it.moveToNext()) {
                    val filePath = it.getString(columnIndex)
                    if (!uploadedImages.contains(filePath)) {
                        imagePaths.add(filePath)
                    }
                }
            }
            return imagePaths
        }
    }
}