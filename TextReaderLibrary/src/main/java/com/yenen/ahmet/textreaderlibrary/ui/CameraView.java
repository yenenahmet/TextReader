package com.yenen.ahmet.textreaderlibrary.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.huawei.hms.mlsdk.common.LensEngine;
import com.huawei.hms.mlsdk.common.MLAnalyzer;
import com.huawei.hms.mlsdk.text.MLText;
import com.huawei.hms.mlsdk.text.MLTextAnalyzer;
import com.yenen.ahmet.textreaderlibrary.R;
import com.yenen.ahmet.textreaderlibrary.camera.CameraSource;
import com.yenen.ahmet.textreaderlibrary.camera.CameraSourcePreview;
import com.yenen.ahmet.textreaderlibrary.other.GraphicOverlay;
import com.yenen.ahmet.textreaderlibrary.text.TextRecognitionProcessor;
import com.yenen.ahmet.textreaderlibrary.util.OcrHelper;
import com.yenen.ahmet.textreaderlibrary.util.PlatformUtil;

import org.jmrtd.lds.icao.MRZInfo;

import java.io.IOException;

public class CameraView extends Fragment implements TextRecognitionProcessor.ResultListener {

    public static final String TYPE_PASSPORT = "P<";
    public static final String TYPE_ID_CARD = "I<";

    private CameraSource cameraSource;
    private GraphicOverlay graphicOverlay;
    private CameraSourcePreview cameraSourcePreview;
    private final OcrDetectorProcessor ocrDetectorProcessor = new OcrDetectorProcessor();
    private MLTextAnalyzer analyzer;
    private SurfaceView surfaceView;
    private LensEngine lensEngine;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private CameraViewResult cameraViewResult;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_camera_view, container, false);
        cameraSourcePreview = view.findViewById(R.id.camera_source_preview);
        if (PlatformUtil.getPlatformType(getContext()) == PlatformUtil.GOOGLE) {
            cameraSourcePreview.setVisibility(View.VISIBLE);
            graphicOverlay = view.findViewById(R.id.graphics_overlay);
            createCameraSource();
            startCameraSource();
        } else {
            cameraSourcePreview.setVisibility(View.GONE);
            surfaceView = view.findViewById(R.id.surfaceView);
            surfaceView.setVisibility(View.VISIBLE);
        }
        return view;
    }

    private void createCameraSource() {
        cameraSource = new CameraSource(getActivity(), graphicOverlay);
        cameraSource.setMachineLearningFrameProcessor(new TextRecognitionProcessor(this));
    }

    @Override
    public void onSuccess(MRZInfo mrzInfo) {
        if (cameraViewResult != null) {
            cameraViewResult.onResult(mrzInfo);
        }
    }

    @Override
    public void onError(Exception exp) {
        if (cameraViewResult != null) {
            cameraViewResult.onError(exp);
        }
    }

    private void startCameraSource() {
        try {
            cameraSourcePreview.start(cameraSource, graphicOverlay);
        } catch (Exception ex) {
            if (cameraSource != null) {
                cameraSource.release();
                cameraSource = null;
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (cameraSourcePreview != null) {
            cameraSourcePreview.stop();
            cameraSourcePreview = null;
        }
        stop();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (PlatformUtil.getPlatformType(getContext()) == PlatformUtil.GOOGLE) {
            startCameraSource();
        } else {
            handler.postDelayed(() -> cameraStream(surfaceView), 100);
        }
    }

    private void cameraStream(final SurfaceView surfaceView) {
        if (getActivity() != null) {
            if (analyzer != null) {
                stop();
            }
            create(surfaceView);
        }
    }

    private void create(final SurfaceView surfaceView) {
        analyzer = new MLTextAnalyzer.Factory(getContext()).create();
        analyzer.setTransactor(ocrDetectorProcessor);

        lensEngine = new LensEngine.Creator(getActivity().getApplicationContext(), analyzer)
                .setLensType(LensEngine.BACK_LENS)
                .applyDisplayDimension(1440, 1080)
                .applyFps(20.0f)
                .enableAutomaticFocus(false).create();
        try {
            lensEngine.run(surfaceView.getHolder());
        } catch (IOException ex) {
            Log.e("LengEn", ex.toString());
        }
    }

    private void stop() {
        if (PlatformUtil.getPlatformType(getContext()) == PlatformUtil.HUAWEI) {
            if (analyzer != null) {
                try {
                    analyzer.release();
                } catch (Exception ignored) {

                }
            }

            if (lensEngine != null) {
                try {
                    lensEngine.release();
                } catch (Exception ignored) {

                }
            }
        }
    }


    class OcrDetectorProcessor implements MLAnalyzer.MLTransactor<MLText.Block> {
        private final OcrHelper ocrHelper = new OcrHelper();

        @Override
        public void destroy() {

        }

        @Override
        public void transactResult(MLAnalyzer.Result<MLText.Block> result) {
            if (result == null || result.getAnalyseList() == null) {
                return;
            }
            final SparseArray<MLText.Block> items = result.getAnalyseList();
            if (items != null && items.size() > 0) {
                for (int i = 0; i < items.size(); i++) {
                    final MLText.Block item = items.get(i);
                    if (item != null) {
                        final String itemText = getReplaceString(item.getStringValue());

                        if (itemText.length() > 50 && (itemText.startsWith(TYPE_PASSPORT) || itemText.startsWith(TYPE_ID_CARD))) {
                            final String docPrefix = itemText.contains(TYPE_PASSPORT) ? TYPE_PASSPORT : TYPE_ID_CARD;
                            final String scannedTextBuffer = itemText.substring(itemText.indexOf(docPrefix));
                            finishScanning(scannedTextBuffer);
                        }
                    }

                }
            }

        }

        private String getReplaceString(final String text) {
            return text.replace(" ", "<").replace("*", "<")
                    .replace("(", "<")
                    .replace("‹", "<")
                    .replace(".", "<");
        }

        private void finishScanning(final String mrzText) {
            try {
                final MRZInfo mrzInfo = new MRZInfo(mrzText);
                final String docNumber = mrzInfo.getDocumentNumber();
                final String newDocNumber = ocrHelper.controlDocumentNumber(docNumber, mrzText);
                if (ocrHelper.isMrzValid(mrzInfo) && newDocNumber != null) {
                    mrzInfo.setDocumentNumber(newDocNumber);

                    handler.postDelayed(() -> {
                        if (cameraViewResult != null) {
                            cameraViewResult.onResult(mrzInfo);
                        }
                    }, 1000);
                }

            } catch (Exception exp) {
                if (cameraViewResult != null) {
                    cameraViewResult.onError(exp);
                }
            }
        }
    }

    public void setCameraViewResult(CameraViewResult cameraViewResult) {
        this.cameraViewResult = cameraViewResult;
    }

    public interface CameraViewResult {
        void onResult(MRZInfo mrzInfo);
        void onError(Exception ex);
    }

}
