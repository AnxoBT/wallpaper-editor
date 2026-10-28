package com.wallpaperfit.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import com.wallpaperfit.app.R
import com.wallpaperfit.app.ui.theme.LogoBottom
import com.wallpaperfit.app.ui.theme.LogoTop

/** El icono de la app dibujado en Compose: mismo primer plano vectorial sobre el fondo grafito. */
@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 28))
            .background(Brush.linearGradient(listOf(LogoTop, LogoBottom))),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
