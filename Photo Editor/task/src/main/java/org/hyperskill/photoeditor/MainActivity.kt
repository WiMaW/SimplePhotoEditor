package org.hyperskill.photoeditor

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import androidx.core.graphics.drawable.toBitmap
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity() {

    private lateinit var currentImage: ImageView
    private lateinit var galleryButton: Button
    private lateinit var sliderBrightness: Slider
    private lateinit var sliderContrast: Slider
    private lateinit var saveButton: Button

    private lateinit var photoUri: Uri
    private var originalImage: Bitmap? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()

        //do not change this line
        currentImage.setImageBitmap(createBitmap())
        originalImage = createBitmap()

        val activityResultLauncher =
            registerForActivityResult(StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    photoUri = result.data?.data ?: return@registerForActivityResult
                    currentImage.setImageURI(photoUri)
                    originalImage = settingOriginalImage(this, photoUri) ?: createBitmap()
                }
            }

        galleryButton.setOnClickListener {
            intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            activityResultLauncher.launch(intent)
        }

        saveButton.setOnClickListener {
            if (hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

                val bitmap: Bitmap = currentImage.drawable.toBitmap()
                val values = ContentValues()
                values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                values.put(MediaStore.Images.ImageColumns.WIDTH, bitmap.width)
                values.put(MediaStore.Images.ImageColumns.HEIGHT, bitmap.height)

                val uri = this@MainActivity.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                ) ?: return@setOnClickListener

                contentResolver.openOutputStream(uri).use {
                    if (it != null) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                    }
                }
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    0
                )
            }
        }

        sliderBrightness.addOnChangeListener { _, value, _ ->
            currentImage.setImageBitmap(imageFilterBrightness(value))
        }

        sliderContrast.addOnChangeListener { _, value, _ ->
            currentImage.setImageBitmap(imageFilterContrast(value, calculateAvgBrightness()))
        }
    }

    private fun bindViews() {
        currentImage = findViewById(R.id.ivPhoto)
        galleryButton = findViewById(R.id.btnGallery)
        sliderBrightness = findViewById(R.id.slBrightness)
        saveButton = findViewById(R.id.btnSave)
        sliderContrast = findViewById(R.id.slContrast)
    }

    // do not change this function
    fun createBitmap(): Bitmap {
        val width = 200
        val height = 100
        val pixels = IntArray(width * height)
        // get pixel array from source

        var R: Int
        var G: Int
        var B: Int
        var index: Int

        for (y in 0 until height) {
            for (x in 0 until width) {
                // get current index in 2D-matrix
                index = y * width + x
                // get color
                R = x % 100 + 40
                G = y % 100 + 80
                B = (x + y) % 100 + 120

                pixels[index] = Color.rgb(R, G, B)

            }
        }
        // output bitmap
        val bitmapOut = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        bitmapOut.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmapOut
    }

    private fun settingOriginalImage(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream).also {
                inputStream?.close()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun hasPermission(manifestPermission: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            this.checkSelfPermission(manifestPermission) == PackageManager.PERMISSION_GRANTED
        } else {
            PermissionChecker.checkSelfPermission(
                this,
                manifestPermission
            ) == PermissionChecker.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResult: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResult)

        if (requestCode == 0) {
            if (grantResult.isNotEmpty() && grantResult[0] == PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    0
                )
                saveButton.callOnClick()
            } else {
                Toast.makeText(this, "Permission to write images denied.", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun imageFilterBrightness(bright: Float): Bitmap {
        val originalWidth = originalImage!!.width
        val originalHeight = originalImage!!.height
        val newPicture =
            Bitmap.createBitmap(originalWidth, originalHeight, Bitmap.Config.ARGB_8888)
        val brightValue = bright.toInt()


        for (w in 0 until originalWidth) {
            for (h in 0 until originalHeight) {
                val pixelColor = originalImage!!.getPixel(w, h)
                val alpha = Color.alpha(pixelColor)
                val R = (Color.red(pixelColor) + brightValue).coerceIn(0, 255)
                val G = (Color.green(pixelColor) + brightValue).coerceIn(0, 255)
                val B = (Color.blue(pixelColor) + brightValue).coerceIn(0, 255)

                newPicture.setPixel(w, h, Color.argb(alpha, R, G, B))
            }
        }
        return newPicture
    }

    private fun imageFilterContrast(contrast: Float, avgBrightness: Int): Bitmap {
        val originalWidth = originalImage!!.width
        val originalHeight = originalImage!!.height
        val newPicture =
            Bitmap.createBitmap(originalWidth, originalHeight, Bitmap.Config.ARGB_8888)
        val contrastValue = contrast.toInt()

        val alphaContrast: Double = (255.0 + contrastValue) / (255 - contrastValue)

        for (w in 0 until originalWidth) {
            for (h in 0 until originalHeight) {
                val pixelColor = originalImage!!.getPixel(w, h)

                val alpha = Color.alpha(pixelColor)

                val R = (alphaContrast.toInt() * (Color.red(pixelColor) - avgBrightness) + avgBrightness).coerceIn(0, 255)

                val G = (alphaContrast.toInt() * (Color.green(pixelColor) - avgBrightness) + avgBrightness).coerceIn(0, 255)

                val B = (alphaContrast.toInt() * (Color.blue(pixelColor) - avgBrightness) + avgBrightness).coerceIn(0, 255)

                newPicture.setPixel(w, h, Color.argb(alpha, R, G, B))
            }
        }
        return newPicture
    }

    private fun calculateAvgBrightness() : Int {
        val originalWidth = originalImage!!.width
        val originalHeight = originalImage!!.height
        var pixelsBrightness: Long = 0

        for (w in 0 until originalWidth) {
            for (h in 0 until originalHeight) {
                val pixelColor = originalImage!!.getPixel(w, h)
                //  var alpha = Color.alpha(pixelColor)
                val R = Color.red(pixelColor)
                val G = Color.green(pixelColor)
                val B = Color.blue(pixelColor)

                pixelsBrightness += R + G + B
            }
        }
        pixelsBrightness /= (originalWidth * originalHeight * 3)

        return pixelsBrightness.toInt().coerceIn(0, 255)
    }
}