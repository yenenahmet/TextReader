package com.yenen.ahmet.textreaderlibrary.text;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;


import com.google.android.gms.tasks.Task;
import com.google.mlkit.common.MlKitException;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.yenen.ahmet.textreaderlibrary.other.FrameMetadata;
import com.yenen.ahmet.textreaderlibrary.other.GraphicOverlay;
import com.yenen.ahmet.textreaderlibrary.ui.CameraView;
import com.yenen.ahmet.textreaderlibrary.util.OcrHelper;

import org.jmrtd.lds.icao.MRZInfo;

import java.nio.ByteBuffer;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class TextRecognitionProcessor {
    private final Handler handler = new Handler(Looper.myLooper());
    private static final String TAG = TextRecognitionProcessor.class.getName();
    private final OcrHelper ocrHelper = new OcrHelper();
    private final TextRecognizer textRecognizer;

    private final ResultListener resultListener;

    private final StringBuilder scannedTextBuffer = new StringBuilder();


    // Whether we should ignore process(). This is usually caused by feeding input data faster than
    // the model can handle.
    private final AtomicBoolean shouldThrottle = new AtomicBoolean(false);


    public TextRecognitionProcessor(ResultListener resultListener) {
        this.resultListener = resultListener;
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    //region ----- Exposed Methods -----


    public void stop() {
        textRecognizer.close();
    }


    public void process(ByteBuffer data, FrameMetadata frameMetadata, GraphicOverlay graphicOverlay) throws MlKitException {

        if (shouldThrottle.get()) {
            return;
        }

        final InputImage inputImage = InputImage.fromByteBuffer(data,
                frameMetadata.getWidth(),
                frameMetadata.getHeight(),
                frameMetadata.getRotation(),
                InputImage.IMAGE_FORMAT_NV21);

        detectInVisionImage(inputImage, frameMetadata, graphicOverlay);
    }

    //endregion

    //region ----- Helper Methods -----

    private Task<Text> detectInImage(InputImage image) {
        return textRecognizer.process(image);
    }


    private void onSuccess(@NonNull Text results, @NonNull FrameMetadata frameMetadata, @NonNull GraphicOverlay graphicOverlay) {

        graphicOverlay.clear();

        scannedTextBuffer.setLength(0);

        final List<Text.TextBlock> blocks = results.getTextBlocks();

        for (int i = 0; i < blocks.size(); i++) {
            final List<Text.Line> lines = blocks.get(i).getLines();
            for (int j = 0; j < lines.size(); j++) {
                final List<Text.Element> elements = lines.get(j).getElements();
                for (int k = 0; k < elements.size(); k++) {
                    filterScannedText(graphicOverlay, elements.get(k));
                }
            }
        }
    }

    private void filterScannedText(final GraphicOverlay graphicOverlay, final Text.Element element) {
        final GraphicOverlay.Graphic textGraphic = new TextGraphic(graphicOverlay, element, Color.GREEN);
        scannedTextBuffer.append(element.getText());
        final String scannedText = scannedTextBuffer.toString();
        if (scannedText.contains(CameraView.TYPE_PASSPORT) || scannedText.contains(CameraView.TYPE_ID_CARD)) {
            graphicOverlay.add(textGraphic);
            final String docPrefix = scannedText.contains(CameraView.TYPE_PASSPORT) ? CameraView.TYPE_PASSPORT : CameraView.TYPE_ID_CARD;
            final String scannedTextBuffer = scannedText.substring(scannedText.indexOf(docPrefix));
            finishScanning(scannedTextBuffer);
        }
    }

    private void onFailure(@NonNull Exception e) {
        Log.w(TAG, "Text detection failed." + e);
        resultListener.onError(e);
    }

    private void detectInVisionImage(InputImage image, final FrameMetadata metadata, final GraphicOverlay graphicOverlay) {

        detectInImage(image)
                .addOnSuccessListener(
                        results -> {
                            shouldThrottle.set(false);
                            TextRecognitionProcessor.this.onSuccess(results, metadata, graphicOverlay);
                        })
                .addOnFailureListener(
                        e -> {
                            shouldThrottle.set(false);
                            TextRecognitionProcessor.this.onFailure(e);
                        });
        // Begin throttling until this frame of input has been processed, either in onSuccess or
        // onFailure.
        shouldThrottle.set(true);
    }

    private void finishScanning(final String mrzText) {
        try {
            final MRZInfo mrzInfo = new MRZInfo(mrzText);
            final String docNumber = mrzInfo.getDocumentNumber();
            final String newDocNumber = ocrHelper.controlDocumentNumber(docNumber, mrzText);
            if (ocrHelper.isMrzValid(mrzInfo) && newDocNumber != null) {
                mrzInfo.setDocumentNumber(newDocNumber);
                // Delay returning result 1 sec. in order to make mrz text become visible on graphicOverlay by user
                // You want to call 'resultListener.onSuccess(mrzInfo)' without no delay

                handler.postDelayed(() -> resultListener.onSuccess(mrzInfo), 1000);
            }

        } catch (Exception exp) {
            Log.d(TAG, "MRZ DATA is not valid");
        }
    }



    public interface ResultListener {
        void onSuccess(MRZInfo mrzInfo);

        void onError(Exception exp);
    }
}

