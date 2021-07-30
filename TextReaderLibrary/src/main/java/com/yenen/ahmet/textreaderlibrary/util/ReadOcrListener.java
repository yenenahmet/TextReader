package com.yenen.ahmet.textreaderlibrary.util;

import com.yenen.ahmet.textreaderlibrary.model.EDocument;
import com.yenen.ahmet.textreaderlibrary.model.ReadOcrStep;

public interface ReadOcrListener {
    void onLoading(ReadOcrStep readOcrStep);
    void onSuccess(EDocument eDocument);
    void onFail(Exception exception);
}
