package com.jatri.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.jatri.core.designsystem.theme.JatriShapes

/** Ticket QR (ZXing). Always black on white, even in dark mode, so scanners can read it. */
@Composable
fun QrCode(payload: String, contentDescription: String, modifier: Modifier = Modifier, size: Dp = 168.dp) {
    val bitmap = remember(payload) { qrBitmap(payload) }
    Box(
        modifier
            .background(Color.White, JatriShapes.cardSmall)
            .padding(8.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Image(bitmap, contentDescription = null, filterQuality = FilterQuality.None, modifier = Modifier.size(size))
    }
}

internal fun qrBitmap(payload: String, px: Int = 512): ImageBitmap {
    val matrix = QRCodeWriter().encode(
        payload,
        BarcodeFormat.QR_CODE,
        px,
        px,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1),
    )
    val pixels = IntArray(matrix.width * matrix.height) { i ->
        if (matrix.get(i % matrix.width, i / matrix.width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
    return android.graphics.Bitmap.createBitmap(pixels, matrix.width, matrix.height, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}
