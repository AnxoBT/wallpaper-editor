package androidx.activity.compose

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/** Equivale al botón Atrás: en el PC lo dispara la tecla Esc. Manda el último que se registró. */
object BackDispatcher {
    internal class Entry(var enabled: () -> Boolean, var action: () -> Unit)

    internal val entries = mutableStateListOf<Entry>()

    fun onBack(): Boolean {
        val entry = entries.lastOrNull { it.enabled() } ?: return false
        entry.action()
        return true
    }
}

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentBack by rememberUpdatedState(onBack)
    DisposableEffect(Unit) {
        val entry = BackDispatcher.Entry({ currentEnabled }, { currentBack() })
        BackDispatcher.entries += entry
        onDispose { BackDispatcher.entries -= entry }
    }
}

@Composable
fun <I, O> rememberLauncherForActivityResult(contract: ActivityResultContract<I, O>, onResult: (O) -> Unit): ActivityResultLauncher<I> {
    val callback by rememberUpdatedState(onResult)
    return remember(contract) {
        object : ActivityResultLauncher<I>() {
            override fun launch(input: I) = callback(contract.run(input))
        }
    }
}
