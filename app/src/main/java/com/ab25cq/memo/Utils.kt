package com.ab25cq.memo

import android.graphics.Bitmap
import android.os.Build

/** API 24-29 では WEBP（非推奨だが動作する）、API 30+ では WEBP_LOSSY を返す */
val WEBP_FORMAT: Bitmap.CompressFormat
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Bitmap.CompressFormat.WEBP_LOSSY
    } else {
        @Suppress("DEPRECATION")
        Bitmap.CompressFormat.WEBP
    }
