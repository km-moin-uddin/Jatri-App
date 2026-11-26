package com.jatri.feature.booking

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jatri.core.common.format.localized
import com.jatri.core.designsystem.component.JatriCard
import com.jatri.core.designsystem.component.JatriOutlinedButton
import com.jatri.core.designsystem.component.JatriTextField
import com.jatri.core.designsystem.icon.JatriIcons
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.ui.placeholder.PhasePlaceholderScreen

/** Layout preview of the passenger form. Fields are disabled until validation lands in Phase 3. */
@Composable
fun PassengerDetailsScreen(onBack: () -> Unit, onContinue: () -> Unit) {
    PhasePlaceholderScreen(
        title = stringResource(R.string.booking_title),
        phase = stringResource(R.string.booking_phase),
        onBack = onBack,
        primaryActionText = stringResource(R.string.booking_continue),
        onPrimaryAction = onContinue,
    ) {
        JatriOutlinedButton(text = stringResource(R.string.booking_saved_traveller), onClick = {}, enabled = false, leadingIcon = JatriIcons.Group)
        JatriCard {
            Text(stringResource(R.string.booking_passenger, 1.localized(JatriTheme.language)), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.xs))
            JatriTextField(value = "", onValueChange = {}, label = stringResource(R.string.booking_full_name), enabled = false)
            JatriTextField(value = "", onValueChange = {}, label = stringResource(R.string.booking_gender), enabled = false)
            JatriTextField(value = "", onValueChange = {}, label = stringResource(R.string.booking_nid), enabled = false)
        }
        JatriCard {
            Text(stringResource(R.string.booking_contact), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.xs))
            JatriTextField(value = "", onValueChange = {}, label = stringResource(R.string.booking_phone), enabled = false)
            JatriTextField(value = "", onValueChange = {}, label = stringResource(R.string.booking_email), enabled = false)
        }
    }
}
