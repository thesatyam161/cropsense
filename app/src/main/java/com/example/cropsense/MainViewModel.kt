package com.example.cropsense

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cropsense.ml.DiseaseClassifier
import com.example.cropsense.model.PredictionResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {

    val result = mutableStateOf<PredictionResponse?>(null)
    val loading = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)

    private var diseaseClassifier: DiseaseClassifier? = null

    fun uploadImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            // Clear stale state before a new analysis
            result.value = null
            error.value = null
            loading.value = true

            try {
                if (diseaseClassifier == null) {
                    // Initialize lazily
                    diseaseClassifier = DiseaseClassifier(context)
                }

                // Run inference on a background thread
                val classificationResult = withContext(Dispatchers.IO) {
                    diseaseClassifier!!.classify(context, uri)
                }

                if (classificationResult.isLeaf) {
                    result.value = PredictionResponse(
                        is_leaf = true,
                        crop = classificationResult.crop,
                        disease = classificationResult.disease,
                        confidence = classificationResult.confidence,
                        message = classificationResult.message
                    )
                } else {
                    result.value = PredictionResponse(
                        is_leaf = false,
                        message = classificationResult.message ?: "Please upload a leaf image for correct diagnosis"
                    )
                }
            } catch (e: Exception) {
                error.value = "Something went wrong during offline analysis. Please try again."
                e.printStackTrace()
            } finally {
                loading.value = false
            }
        }
    }

    fun clearAll() {
        result.value = null
        error.value = null
    }
}
