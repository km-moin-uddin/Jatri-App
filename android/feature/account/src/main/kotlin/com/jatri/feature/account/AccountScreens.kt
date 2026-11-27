package com.jatri.feature.account

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jatri.core.designsystem.component.JatriCard
import com.jatri.core.designsystem.component.JatriCtaButton
import com.jatri.core.designsystem.component.JatriSmallButton
import com.jatri.core.designsystem.component.JatriTextField
import com.jatri.core.common.format.localizeDigits
import com.jatri.core.model.SessionState
import com.jatri.core.model.User
import com.jatri.core.ui.text
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.jatri.core.designsystem.component.JatriOutlinedButton
import com.jatri.core.designsystem.component.JatriPrimaryButton
import com.jatri.core.designsystem.component.JatriTopAppBar
import com.jatri.core.designsystem.icon.JatriIcons
import com.jatri.core.designsystem.theme.JatriTheme
import com.jatri.core.designsystem.theme.Spacing
import com.jatri.core.model.AppLanguage
import com.jatri.core.model.ThemeMode
import com.jatri.core.model.UserSettings
import com.jatri.core.ui.placeholder.PhasePlaceholderScreen

/** Kept so R8 doesn't rename it: it is a navigation argument. */
@androidx.annotation.Keep
@kotlinx.serialization.Serializable
enum class AccountSubPage { PROFILE, SAVED_TRAVELLERS, PRICE_ALERTS, PAYMENT_HISTORY, PRIVACY, TERMS }

@Composable
internal fun AccountRoute(
    onLoginClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onOpenPage: (AccountSubPage) -> Unit,
    onHelpClick: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    error?.let { e ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            text = { Text(e.text()) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.account_cancel)) } },
        )
    }
    AccountScreen(
        settings = settings,
        onLoginClick = onLoginClick,
        onNotificationsClick = onNotificationsClick,
        onOpenPage = onOpenPage,
        onHelpClick = onHelpClick,
        onLanguageChange = viewModel::setLanguage,
        onThemeChange = viewModel::setTheme,
        onPushChange = viewModel::setNotifications,
        session = session,
        onEditProfile = { onOpenPage(AccountSubPage.PROFILE) },
        onLogout = viewModel::logout,
        onDeleteAccount = viewModel::deleteAccount,
        busy = busy,
    )
}

private enum class AccountDialog { NONE, LANGUAGE, THEME, LOGOUT, DELETE }

@Composable
fun AccountScreen(
    settings: UserSettings,
    onLoginClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onOpenPage: (AccountSubPage) -> Unit,
    onHelpClick: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onPushChange: (Boolean) -> Unit,
    session: SessionState = SessionState.LoggedOut,
    onEditProfile: () -> Unit = {},
    onLogout: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    busy: Boolean = false,
) {
    var dialog by rememberSaveable { mutableStateOf(AccountDialog.NONE) }
    Scaffold(topBar = { JatriTopAppBar(title = stringResource(R.string.account_title)) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            when (session) {
                is SessionState.LoggedIn -> ProfileCard(session.user, onEditProfile)
                SessionState.Unknown -> Unit
                SessionState.LoggedOut -> GuestCard(onLoginClick)
            }

            SettingsGroup(stringResource(R.string.account_section_travel)) {
                SettingsRow(JatriIcons.Group, stringResource(R.string.account_saved_travellers)) { onOpenPage(AccountSubPage.SAVED_TRAVELLERS) }
                SettingsRow(JatriIcons.NotificationsOutlined, stringResource(R.string.alerts_title)) { onOpenPage(AccountSubPage.PRICE_ALERTS) }
                SettingsRow(JatriIcons.Payments, stringResource(R.string.account_payment_history)) { onOpenPage(AccountSubPage.PAYMENT_HISTORY) }
                SettingsRow(JatriIcons.Notifications, stringResource(R.string.account_notifications), onClick = onNotificationsClick)
            }

            SettingsGroup(stringResource(R.string.account_section_settings)) {
                SettingsRow(
                    JatriIcons.Language,
                    stringResource(R.string.account_language),
                    value = if (settings.language == AppLanguage.BANGLA) "বাংলা" else "English",
                ) { dialog = AccountDialog.LANGUAGE }
                SettingsRow(JatriIcons.DarkMode, stringResource(R.string.account_theme), value = themeLabel(settings.themeMode)) {
                    dialog = AccountDialog.THEME
                }
                SwitchRow(JatriIcons.Notifications, stringResource(R.string.account_push), settings.notificationsEnabled, onPushChange)
            }

            SettingsGroup(stringResource(R.string.account_section_support)) {
                SettingsRow(JatriIcons.Help, stringResource(R.string.account_help), onClick = onHelpClick)
                SettingsRow(JatriIcons.Policy, stringResource(R.string.account_privacy)) { onOpenPage(AccountSubPage.PRIVACY) }
                SettingsRow(JatriIcons.Document, stringResource(R.string.account_terms)) { onOpenPage(AccountSubPage.TERMS) }
            }

            if (session is SessionState.LoggedIn) {
                SettingsGroup(stringResource(R.string.account_section_account)) {
                    SettingsRow(JatriIcons.Logout, stringResource(R.string.account_logout)) { dialog = AccountDialog.LOGOUT }
                    SettingsRow(
                        JatriIcons.Delete,
                        stringResource(R.string.account_delete),
                        danger = true,
                    ) { dialog = AccountDialog.DELETE }
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }

    when (dialog) {
        AccountDialog.LANGUAGE -> ChoiceDialog(
            title = stringResource(R.string.account_language),
            options = AppLanguage.entries,
            selected = settings.language,
            label = { if (it == AppLanguage.BANGLA) "বাংলা" else "English" },
            onSelect = { onLanguageChange(it); dialog = AccountDialog.NONE },
            onDismiss = { dialog = AccountDialog.NONE },
        )
        AccountDialog.THEME -> ChoiceDialog(
            title = stringResource(R.string.account_theme),
            options = ThemeMode.entries,
            selected = settings.themeMode,
            label = { themeLabel(it) },
            onSelect = { onThemeChange(it); dialog = AccountDialog.NONE },
            onDismiss = { dialog = AccountDialog.NONE },
        )
        AccountDialog.LOGOUT -> AlertDialog(
            onDismissRequest = { dialog = AccountDialog.NONE },
            text = { Text(stringResource(R.string.account_logout_confirm)) },
            confirmButton = {
                TextButton(onClick = { dialog = AccountDialog.NONE; onLogout() }) { Text(stringResource(R.string.account_logout)) }
            },
            dismissButton = { TextButton(onClick = { dialog = AccountDialog.NONE }) { Text(stringResource(R.string.account_cancel)) } },
        )
        AccountDialog.DELETE -> AlertDialog(
            onDismissRequest = { dialog = AccountDialog.NONE },
            title = { Text(stringResource(R.string.account_delete_title)) },
            text = { Text(stringResource(R.string.account_delete_body)) },
            confirmButton = {
                TextButton(onClick = { dialog = AccountDialog.NONE; onDeleteAccount() }, enabled = !busy) {
                    Text(stringResource(R.string.account_delete_confirm), color = JatriTheme.colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { dialog = AccountDialog.NONE }) { Text(stringResource(R.string.account_cancel)) } },
        )
        AccountDialog.NONE -> Unit
    }
}

@Composable
private fun themeLabel(mode: ThemeMode) = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.account_theme_system
        ThemeMode.LIGHT -> R.string.account_theme_light
        ThemeMode.DARK -> R.string.account_theme_dark
    },
)

@Composable
private fun ProfileCard(user: User, onEdit: () -> Unit) {
    val lang = JatriTheme.language
    JatriCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Text(
                    user.name?.trim()?.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    user.name ?: stringResource(R.string.account_no_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (user.name == null) JatriTheme.colors.textSecondary else MaterialTheme.colorScheme.onSurface,
                )
                Text("+88 ${user.phone}".localizeDigits(lang), style = MaterialTheme.typography.bodyMedium, color = JatriTheme.colors.textSecondary)
            }
            JatriSmallButton(text = stringResource(R.string.account_edit_profile), onClick = onEdit)
        }
    }
}

@Composable
private fun GuestCard(onLoginClick: () -> Unit) {
    JatriCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(JatriIcons.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.account_guest), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.account_guest_body), style = MaterialTheme.typography.bodySmall, color = JatriTheme.colors.textSecondary)
            }
        }
        Spacer(Modifier.size(Spacing.sm))
        JatriPrimaryButton(text = stringResource(R.string.account_login), onClick = onLoginClick)
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = JatriTheme.colors.textSecondary,
            modifier = Modifier.padding(start = Spacing.xxs, bottom = Spacing.xs).semantics { heading() },
        )
        JatriCard(contentPadding = PaddingValues(vertical = Spacing.xxs)) { content() }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, value: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (danger) JatriTheme.colors.danger else MaterialTheme.colorScheme.primary
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(Spacing.md))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = if (danger) tint else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = JatriTheme.colors.textSecondary)
            Spacer(Modifier.width(Spacing.xxs))
        }
        Icon(JatriIcons.ChevronRight, contentDescription = null, tint = JatriTheme.colors.textSecondary)
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Spacing.md))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Spacing.touchTarget)
                            .selectable(selected = option == selected, role = Role.RadioButton, onClick = { onSelect(option) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Spacer(Modifier.width(Spacing.sm))
                        Text(label(option), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.account_cancel)) } },
    )
}

@Composable
internal fun ProfileRoute(onBack: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val lang = JatriTheme.language
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { JatriTopAppBar(title = stringResource(R.string.account_profile), onBack = onBack) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            JatriTextField(
                value = form.name,
                onValueChange = viewModel::onName,
                label = stringResource(R.string.account_name),
                error = if (form.nameError) stringResource(R.string.account_name_required) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            JatriTextField(
                value = form.email,
                onValueChange = viewModel::onEmail,
                label = stringResource(R.string.account_email),
                error = if (form.emailError) stringResource(R.string.account_email_error) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            )
            JatriTextField(
                value = "+88 ${form.phone}".localizeDigits(lang),
                onValueChange = {},
                label = stringResource(R.string.account_phone),
                enabled = false,
            )
            form.error?.let { Text(it.text(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(Spacing.xs))
            JatriCtaButton(text = stringResource(R.string.account_save), onClick = { viewModel.save(onBack) }, loading = form.saving)
        }
    }
}

@Composable
fun AccountSubPageScreen(page: AccountSubPage, onBack: () -> Unit) {
    val (title, phase) = when (page) {
        AccountSubPage.PROFILE -> R.string.account_title to R.string.account_phase_profile
        AccountSubPage.SAVED_TRAVELLERS, AccountSubPage.PRICE_ALERTS -> R.string.account_saved_travellers to R.string.account_phase_travellers
        AccountSubPage.PAYMENT_HISTORY -> R.string.account_payment_history to R.string.account_phase_payments
        AccountSubPage.PRIVACY -> R.string.account_privacy to R.string.account_phase_legal
        AccountSubPage.TERMS -> R.string.account_terms to R.string.account_phase_legal
    }
    PhasePlaceholderScreen(title = stringResource(title), phase = stringResource(phase), onBack = onBack)
}

/** PLACEHOLDER hotline number — replace with the real support number before release. */
private const val HOTLINE = "+8809600000000"

@Composable
fun HelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    PhasePlaceholderScreen(title = stringResource(R.string.account_help), phase = stringResource(R.string.account_phase_help), onBack = onBack) {
        JatriCard {
            listOf(
                R.string.account_faq1_q to R.string.account_faq1_a,
                R.string.account_faq2_q to R.string.account_faq2_a,
                R.string.account_faq3_q to R.string.account_faq3_a,
            ).forEachIndexed { i, (q, a) ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = Spacing.sm), color = JatriTheme.colors.divider)
                Text(stringResource(q), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(Spacing.xxs))
                Text(stringResource(a), style = MaterialTheme.typography.bodyMedium, color = JatriTheme.colors.textSecondary)
            }
        }
        JatriPrimaryButton(
            text = stringResource(R.string.account_call_hotline),
            leadingIcon = JatriIcons.Phone,
            onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$HOTLINE".toUri())) },
        )
        JatriOutlinedButton(text = stringResource(R.string.account_live_chat), onClick = {}, enabled = false, leadingIcon = JatriIcons.Support)
        JatriOutlinedButton(text = stringResource(R.string.account_report_problem), onClick = {}, enabled = false, leadingIcon = JatriIcons.Error)
    }
}
