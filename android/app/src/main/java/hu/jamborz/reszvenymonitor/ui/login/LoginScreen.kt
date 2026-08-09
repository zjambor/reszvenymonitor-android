package hu.jamborz.reszvenymonitor.ui.login

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.jamborz.reszvenymonitor.R
import hu.jamborz.reszvenymonitor.data.AuthRepository
import hu.jamborz.reszvenymonitor.ui.theme.LocalMonitorColors
import hu.jamborz.reszvenymonitor.ui.theme.auroraBackground

/**
 * A webes login-overlay megfelelője: középre igazított kártya, felhasználónév +
 * jelszó, egyetlen általános hibaüzenet, hibánál kártya-rázás. Sikeres belépésig
 * az app semmilyen adatkérést nem indít (a gyökér-kapu garantálja).
 *
 * @param sessionExpired ha futás közben veszett el a session (401/refresh-hiba),
 * a képernyő a „Lejárt a munkamenet" üzenettel nyit.
 */
@Composable
fun LoginScreen(
    uiState: AuthViewModel.LoginUiState,
    sessionExpired: Boolean,
    onSignIn: (String, String) -> Unit,
) {
    val palette = LocalMonitorColors.current
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    // Kártya-rázás hibánál (webes shake-animáció portja: -9 → 8 → -6 → 4 → 0 px)
    val shakeOffset = remember { Animatable(0f) }
    val shakePx = with(LocalDensity.current) { 1.dp.toPx() }
    LaunchedEffect(uiState.shakeNonce) {
        if (uiState.shakeNonce > 0) {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 350
                    (-9f * shakePx) at 70
                    (8f * shakePx) at 140
                    (-6f * shakePx) at 210
                    (4f * shakePx) at 280
                    0f at 350
                },
            )
        }
    }

    val submit = { onSignIn(username, password) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .auroraBackground(palette)
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .widthIn(max = 360.dp)
                .graphicsLayer { translationX = shakeOffset.value }
                .clip(MaterialTheme.shapes.medium)
                // .login-card: rgba(23,28,52,.96) → rgba(13,17,34,.96)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xF5171C34), Color(0xF50D1122))
                    )
                )
                .border(1.dp, palette.border, MaterialTheme.shapes.medium)
                .padding(start = 26.dp, top = 28.dp, end = 26.dp, bottom = 26.dp),
        ) {
            Text(
                text = stringResource(R.string.login_title),
                style = MaterialTheme.typography.titleLarge,
                color = palette.text,
            )
            Text(
                text = stringResource(R.string.login_sub),
                style = MaterialTheme.typography.bodySmall,
                color = palette.textDim,
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
            )

            LoginLabel(stringResource(R.string.login_user_label))
            LoginInput(
                value = username,
                onValueChange = { username = it },
                enabled = !uiState.busy,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )

            LoginLabel(stringResource(R.string.login_pass_label))
            LoginInput(
                value = password,
                onValueChange = { password = it },
                enabled = !uiState.busy,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                visualTransformation = PasswordVisualTransformation(),
            )

            val errorText = when {
                uiState.error != null -> when (uiState.error) {
                    is AuthRepository.SignInError.InvalidCredentials ->
                        stringResource(R.string.login_error_invalid)
                    is AuthRepository.SignInError.Network ->
                        stringResource(R.string.login_error_network)
                }
                sessionExpired -> stringResource(R.string.login_session_expired)
                else -> null
            }
            if (errorText != null) {
                Text(
                    text = errorText,
                    color = palette.down,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            // .btn-primary .login-btn — akcent-hátterű, teljes szélességű gomb
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (uiState.busy) palette.accent.copy(alpha = 0.55f) else palette.accent)
                    .clickable(enabled = !uiState.busy, onClick = submit)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (uiState.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = palette.onAccent,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.login_button),
                        color = palette.onAccent,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginLabel(text: String) {
    val palette = LocalMonitorColors.current
    Text(
        text = text,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = palette.textDim,
        modifier = Modifier.padding(bottom = 5.dp),
    )
}

/** A webes .login-card input portja: sötét mező, fókusznál akcent-keret. */
@Composable
private fun LoginInput(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val palette = LocalMonitorColors.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        interactionSource = interaction,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        textStyle = TextStyle(
            color = palette.text,
            fontSize = 14.sp,
        ),
        cursorBrush = SolidColor(palette.accent),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(Color(0xB80A0D1A)) // rgba(10,13,26,.72)
                    .border(
                        width = 1.dp,
                        color = if (focused) palette.accent else palette.border,
                        shape = shape,
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) { innerTextField() }
        },
    )
}
