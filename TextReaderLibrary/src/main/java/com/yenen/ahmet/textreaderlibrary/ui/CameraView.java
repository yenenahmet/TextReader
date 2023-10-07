package com.yenen.ahmet.textreaderlibrary.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.yenen.ahmet.textreaderlibrary.R;
import com.yenen.ahmet.textreaderlibrary.camera.CameraSource;
import com.yenen.ahmet.textreaderlibrary.camera.CameraSourcePreview;
import com.yenen.ahmet.textreaderlibrary.other.GraphicOverlay;
import com.yenen.ahmet.textreaderlibrary.text.TextRecognitionProcessor;
import com.yenen.ahmet.textreaderlibrary.util.PlatformUtil;

import org.jmrtd.lds.icao.MRZInfo;


public class CameraView extends Fragment implements TextRecognitionProcessor.ResultListener {

    public static final String TYPE_PASSPORT = "P<";
    public static final String TYPE_ID_CARD = "I<";

    private CameraSource cameraSource;
    private GraphicOverlay graphicOverlay;
    private CameraSourcePreview cameraSourcePreview;
    private SurfaceView surfaceView;

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
    }

    @Override
    public void onResume() {
        super.onResume();
        startCameraSource();
    }

    public void setCameraViewResult(CameraViewResult cameraViewResult) {
        this.cameraViewResult = cameraViewResult;
    }

    public interface CameraViewResult {
        void onResult(MRZInfo mrzInfo);

        void onError(Exception ex);
    }

}
