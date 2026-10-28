package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.wallpaperfit.app.desktop.Strings

@Composable
fun stringResource(id: Int): String = Strings.get(id)

@Composable
fun stringResource(id: Int, vararg args: Any?): String = Strings.get(id, *args)

@Composable
fun pluralStringResource(id: Int, count: Int, vararg args: Any?): String = Strings.plural(id, count, *args)

/** El único dibujo de la app es su logo. */
@Composable
fun painterResource(id: Int): Painter = painterResource(Strings.path(id))
