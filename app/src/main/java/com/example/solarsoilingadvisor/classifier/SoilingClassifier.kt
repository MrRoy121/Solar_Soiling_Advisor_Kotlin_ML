package com.example.solarsoilingadvisor.classifier

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

/**
 * Result of classifying one image.
 *
 * @param label              predicted class name (e.g. "Dusty")
 * @param confidence         confidence of the predicted class, 0f..1f
 * @param classIndex         index into the labels list
 * @param probabilities      full probability vector (1 entry for sigmoid, N for softmax)
 * @param dirtinessFraction  continuous "how dirty is the panel" score, 0f..1f.
 *                           For binary sigmoid models this is the raw sigmoid output.
 *                           For multi-class models it is 1 - P(Clean).
 *                           Drives the UI meter and the decision-layer loss scaling.
 */
data class SoilingResult(
    val label: String,
    val confidence: Float,
    val classIndex: Int,
    val probabilities: List<Float>,
    val dirtinessFraction: Float,
)

/**
 * On-device solar-panel soiling classifier.
 *
 * Preprocessing contract: the training notebook bakes each backbone's
 * `preprocess_input` INTO the model graph, so the .tflite expects RAW pixel
 * values in [0, 255]. We deliberately do not normalize here.
 *
 * Output handling:
 *  - Binary models: Dense(1, sigmoid)  -> shape [1, 1]  -> value = P(class 1)
 *  - Multi-class:   Dense(N, softmax)  -> shape [1, N]
 */
class SoilingClassifier(
    context: Context,
    modelAsset: String = "model.tflite",
    labelAsset: String = "labels.txt",
    numThreads: Int = 4,
) {
    private val interpreter: Interpreter
    private val labels: List<String>
    private val inputSize: Int
    private val outputCount: Int
    private val cleanIndex: Int
    private val imageProcessor: ImageProcessor

    init {
        val modelBuffer = FileUtil.loadMappedFile(context, modelAsset)
        interpreter = Interpreter(modelBuffer, Interpreter.Options().apply {
            setNumThreads(numThreads)
        })
        labels = FileUtil.loadLabels(context, labelAsset)

        val inShape = interpreter.getInputTensor(0).shape()    // [1, H, W, 3]
        inputSize = inShape[1]

        val outShape = interpreter.getOutputTensor(0).shape()  // [1, N]
        outputCount = outShape[1]

        // For multi-class dirtiness = 1 - P(Clean), so we need the "Clean" index.
        cleanIndex = labels.indexOfFirst { it.trim().equals("clean", ignoreCase = true) }

        imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.BILINEAR))
            .build()
    }

    fun classify(bitmap: Bitmap): SoilingResult {
        var image = TensorImage(DataType.FLOAT32)
        image.load(bitmap)
        image = imageProcessor.process(image)

        val output = Array(1) { FloatArray(outputCount) }
        interpreter.run(image.buffer, output)
        val probs = output[0]

        val (index, confidence) = if (outputCount == 1) {
            val p = probs[0]
            if (p >= 0.5f) 1 to p else 0 to (1f - p)
        } else {
            var maxIdx = 0
            for (i in probs.indices) if (probs[i] > probs[maxIdx]) maxIdx = i
            maxIdx to probs[maxIdx]
        }
        val label = labels.getOrElse(index) { "class_$index" }

        // Continuous dirtiness score.
        val dirtiness: Float = when {
            outputCount == 1 -> probs[0].coerceIn(0f, 1f)        // sigmoid = P(class 1 == dusty)
            cleanIndex in probs.indices -> (1f - probs[cleanIndex]).coerceIn(0f, 1f)
            else -> if (index == 0) 0f else confidence            // best-effort fallback
        }

        return SoilingResult(
            label = label,
            confidence = confidence,
            classIndex = index,
            probabilities = probs.toList(),
            dirtinessFraction = dirtiness,
        )
    }

    fun close() = interpreter.close()
}