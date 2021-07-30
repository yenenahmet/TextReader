package com.yenen.ahmet.textreaderlibrary.util;

import androidx.annotation.Nullable;

import org.jmrtd.lds.icao.MRZInfo;

public class OcrHelper {
    private final StringBuilder sb = new StringBuilder();

    public boolean isMrzValid(MRZInfo mrzInfo) {
        return mrzInfo.getDateOfBirth() != null &&
                mrzInfo.getDateOfBirth().length() == 6 &&
                isDigitControl(mrzInfo.getDateOfBirth()) &&
                mrzInfo.getDateOfExpiry() != null &&
                mrzInfo.getDateOfExpiry().length() == 6 &&
                isDigitControl(mrzInfo.getDateOfExpiry());
    }

    private boolean isDigitControl(final String text) {
        for (char s : text.toCharArray()) {
            if (!Character.isDigit(s)) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    public String controlDocumentNumber(final String text, final String type) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        if (type != null && type.startsWith("P")) {
            return text;
        }

        if (text.length() >= 8) {
            int i = 0;

            sb.setLength(0);
            for (char s : text.toCharArray()) {
                if (i == 0 || i == 3) {
                    if (s == '0') {
                        s = 'O';
                    }
                    if (Character.isDigit(s)) {
                        return null;
                    }
                }
                sb.append(s);
                i++;
            }
            return sb.toString();
        }

        return null;

    }
}
