package com.jatri.feature.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jatri.core.designsystem.component.PillChip
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.Gender
import com.jatri.core.model.PassengerType
import com.jatri.core.model.SavedTraveller
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** "Fill from a saved traveller" chips at the top of a passenger card. Nothing shows if none are saved. */
@Composable
internal fun SavedTravellerChips(travellers: List<SavedTraveller>, onPick: (SavedTraveller) -> Unit) {
    if (travellers.isEmpty()) return
    Text(
        stringResource(R.string.booking_saved_travellers),
        style = MaterialTheme.typography.labelMedium,
        color = JatriTheme.colors.textSecondary,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        items(travellers, key = { it.id ?: it.fullName }) { t ->
            PillChip(text = t.fullName, selected = false, onClick = { onPick(t) })
        }
    }
}

/** Bus/train card: name as on the NID, gender, age on [day], NID. */
internal fun PassengerForm.fillFrom(t: SavedTraveller, day: LocalDate): PassengerForm = copy(
    name = t.fullName.take(80),
    // A ladies seat only takes a woman; don't put another gender there.
    gender = if (ladiesSeat && t.gender != Gender.FEMALE) gender else t.gender,
    age = t.ageOn(day)?.toString() ?: age,
    nid = t.nid ?: nid,
    nameError = false,
    genderError = false,
    ageError = false,
    nidError = false,
)

private val DIGITS = DateTimeFormatter.ofPattern("ddMMyyyy")

/** Flight card: passport names, gender, DOB, passport (international) or NID (domestic, optional). */
internal fun FlightPassengerForm.fillFrom(t: SavedTraveller, international: Boolean): FlightPassengerForm {
    val latin = Regex("[A-Za-z .'-]+")
    val given = t.givenName ?: t.fullName.takeIf { latin.matches(it) }?.substringBeforeLast(' ', t.fullName)
    val sur = t.surname ?: t.fullName.takeIf { latin.matches(it) && ' ' in it }?.substringAfterLast(' ')
    return copy(
        givenName = given ?: givenName,
        surname = sur ?: surname,
        gender = t.gender,
        dob = t.dateOfBirth?.format(DIGITS) ?: dob,
        documentNo = (if (international) t.passportNo else t.nid) ?: documentNo,
        expiry = if (international) t.passportExpiry?.format(DIGITS) ?: expiry else expiry,
        nationality = t.nationality,
        givenNameError = null,
        surnameError = null,
        genderError = false,
        dobError = null,
        documentError = null,
        expiryError = null,
        nationalityError = false,
    )
}

/** Saved travellers that fit this seat type, by age on the travel day (no DOB = adult). */
internal fun List<SavedTraveller>.fitting(type: PassengerType, day: LocalDate): List<SavedTraveller> = filter { t ->
    val age = t.ageOn(day) ?: return@filter type == PassengerType.ADULT
    when (type) {
        PassengerType.ADULT -> age >= 12
        PassengerType.CHILD -> age in 2..11
        PassengerType.INFANT -> age < 2
    }
}
