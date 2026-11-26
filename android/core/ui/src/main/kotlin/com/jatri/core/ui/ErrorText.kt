package com.jatri.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.model.AppError

/** The message to show for an [AppError], in the current language. */
@Composable
fun AppError.text(): String = when (this) {
    is AppError.Api -> message(JatriTheme.language)
    AppError.Network -> stringResource(R.string.ui_error_network)
    AppError.Unknown -> stringResource(com.jatri.core.designsystem.R.string.ds_error_message)
}
