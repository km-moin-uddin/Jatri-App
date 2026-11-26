package com.jatri.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.jatri.core.designsystem.icon.JatriIcons
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.model.CabinClass
import com.jatri.core.model.CoachType
import com.jatri.core.model.TrainClass
import com.jatri.core.model.TravelMode

val TravelMode.icon: ImageVector
    get() = when (this) {
        TravelMode.FLIGHT -> JatriIcons.Flight
        TravelMode.BUS -> JatriIcons.Bus
        TravelMode.TRAIN -> JatriIcons.Train
    }

@get:StringRes
val TravelMode.labelRes: Int
    get() = when (this) {
        TravelMode.FLIGHT -> R.string.ui_mode_flight
        TravelMode.BUS -> R.string.ui_mode_bus
        TravelMode.TRAIN -> R.string.ui_mode_train
    }

/** Sky blue for flights, leaf green for buses, rail maroon for trains. */
val TravelMode.color: Color
    @Composable @ReadOnlyComposable
    get() = when (this) {
        TravelMode.FLIGHT -> JatriTheme.colors.flight
        TravelMode.BUS -> JatriTheme.colors.bus
        TravelMode.TRAIN -> JatriTheme.colors.train
    }

@get:StringRes
val TravelMode.selectPlaceRes: Int
    get() = when (this) {
        TravelMode.FLIGHT -> R.string.ui_select_airport
        TravelMode.BUS -> R.string.ui_select_city
        TravelMode.TRAIN -> R.string.ui_select_station
    }

@Composable
fun CabinClass.label(): String = stringResource(
    when (this) {
        CabinClass.ECONOMY -> R.string.ui_cabin_economy
        CabinClass.PREMIUM_ECONOMY -> R.string.ui_cabin_premium_economy
        CabinClass.BUSINESS -> R.string.ui_cabin_business
    },
)

@Composable
fun CoachType.label(): String = stringResource(
    when (this) {
        CoachType.ANY -> R.string.ui_coach_any
        CoachType.AC -> R.string.ui_coach_ac
        CoachType.NON_AC -> R.string.ui_coach_non_ac
        CoachType.SLEEPER -> R.string.ui_coach_sleeper
    },
)

@Composable
fun TrainClass.label(): String = stringResource(
    when (this) {
        TrainClass.SHOVAN -> R.string.ui_train_shovan
        TrainClass.SHOVAN_CHAIR -> R.string.ui_train_shovan_chair
        TrainClass.SNIGDHA -> R.string.ui_train_snigdha
        TrainClass.AC_SEAT -> R.string.ui_train_ac_seat
        TrainClass.AC_BERTH -> R.string.ui_train_ac_berth
        TrainClass.F_BERTH -> R.string.ui_train_f_berth
        TrainClass.F_SEAT -> R.string.ui_train_f_seat
    },
)
