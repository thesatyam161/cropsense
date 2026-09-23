package com.example.cropsense.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

data class ClassificationResult(
    val isLeaf: Boolean,
    val prediction: String? = null,
    val crop: String? = null,
    val disease: String? = null,
    val confidence: Float = 0f,
    val message: String? = null
)

class DiseaseClassifier(context: Context) {

    private val leafInterpreter: Interpreter
    private val diseaseInterpreter: Interpreter

    private val classNames = listOf(
        "Apple___Apple_scab", "Apple___Black_rot", "Apple___Cedar_apple_rust", "Apple___healthy",
        "Blueberry___healthy",
        "Cherry_(including_sour)___Powdery_mildew", "Cherry_(including_sour)___healthy",
        "Corn_(maize)___Cercospora_leaf_spot Gray_leaf_spot", "Corn_(maize)___Common_rust_",
        "Corn_(maize)___Northern_Leaf_Blight", "Corn_(maize)___healthy",
        "Grape___Black_rot", "Grape___Esca_(Black_Measles)", "Grape___Leaf_blight_(Isariopsis_Leaf_Spot)", "Grape___healthy",
        "Orange___Haunglongbing_(Citrus_greening)",
        "Peach___Bacterial_spot", "Peach___healthy",
        "Pepper,_bell___Bacterial_spot", "Pepper,_bell___healthy",
        "Potato___Early_blight", "Potato___Late_blight", "Potato___healthy",
        "Raspberry___healthy",
        "Soybean___healthy",
        "Squash___Powdery_mildew",
        "Strawberry___Leaf_scorch", "Strawberry___healthy",
        "Tomato___Bacterial_spot", "Tomato___Early_blight", "Tomato___Late_blight", "Tomato___Leaf_Mold",
        "Tomato___Septoria_leaf_spot", "Tomato___Spider_mites Two-spotted_spider_mite",
        "Tomato___Target_Spot", "Tomato___Tomato_Yellow_Leaf_Curl_Virus",
        "Tomato___Tomato_mosaic_virus", "Tomato___healthy"
    )

    init {
        val leafModel = loadModelFile(context, "plant_detector.tflite")
        val diseaseModel = loadModelFile(context, "model.tflite")
        
        leafInterpreter = Interpreter(leafModel)
        diseaseInterpreter = Interpreter(diseaseModel)
    }

    private fun loadModelFile(context: Context, modelName: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun classify(context: Context, uri: Uri): ClassificationResult {
        val inputStream = context.contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        if (originalBitmap == null) {
            return ClassificationResult(isLeaf = false, message = "Could not decode image")
        }

        val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, 224, 224, true)
        val byteBuffer = convertBitmapToByteBuffer(resizedBitmap)

        // 1. Check if leaf
        val leafOutputShape = leafInterpreter.getOutputTensor(0).shape()
        val numClasses = leafOutputShape[1]
        
        val isLeaf = if (numClasses == 1) {
            val leafOutput = Array(1) { FloatArray(1) }
            leafInterpreter.run(byteBuffer, leafOutput)
            leafOutput[0][0] > 0.5f
        } else {
            val leafOutput = Array(1) { FloatArray(numClasses) }
            leafInterpreter.run(byteBuffer, leafOutput)
            var maxIdx = 0
            var maxVal = leafOutput[0][0]
            for (i in 1 until numClasses) {
                if (leafOutput[0][i] > maxVal) {
                    maxVal = leafOutput[0][i]
                    maxIdx = i
                }
            }
            maxIdx == 0
        }

        if (!isLeaf) {
            return ClassificationResult(isLeaf = false, message = "Please upload a leaf image for correct diagnosis")
        }

        // 2. Predict disease
        val diseaseOutputShape = diseaseInterpreter.getOutputTensor(0).shape()
        val diseaseNumClasses = diseaseOutputShape[1]
        val diseaseOutput = Array(1) { FloatArray(diseaseNumClasses) }
        
        byteBuffer.rewind() // Reset buffer position before second inference
        diseaseInterpreter.run(byteBuffer, diseaseOutput)
        
        var maxIdx = 0
        var maxVal = diseaseOutput[0][0]
        for (i in 1 until diseaseNumClasses) {
            if (diseaseOutput[0][i] > maxVal) {
                maxVal = diseaseOutput[0][i]
                maxIdx = i
            }
        }
        
        val prediction = classNames[maxIdx]
        val parts = prediction.split("___")
        val crop = parts[0]
        val diseaseName = parts[1].replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() } }
        
        return ClassificationResult(
            isLeaf = true,
            prediction = prediction,
            crop = crop,
            disease = diseaseName,
            confidence = maxVal
        )
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * 224 * 224 * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        
        val intValues = IntArray(224 * 224)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        var pixel = 0
        for (i in 0 until 224) {
            for (j in 0 until 224) {
                val value = intValues[pixel++]
                
                // Extract RGB and normalize to 0.0 - 1.0 (float32)
                byteBuffer.putFloat(((value shr 16) and 0xFF) / 255.0f)
                byteBuffer.putFloat(((value shr 8) and 0xFF) / 255.0f)
                byteBuffer.putFloat((value and 0xFF) / 255.0f)
            }
        }
        return byteBuffer
    }
}
