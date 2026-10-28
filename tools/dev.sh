# Ayudas para probar en el móvil. Uso (Git Bash): source tools/dev.sh
# Siempre contra el móvil de pruebas indicado en ANDROID_SERIAL; nunca otros dispositivos de la red.
# El serial va en tools/dev.local.sh (no se sube al repositorio):  export ANDROID_SERIAL=<serial>
_DEV_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
[ -f "$_DEV_DIR/dev.local.sh" ] && source "$_DEV_DIR/dev.local.sh"
[ -n "$ANDROID_SERIAL" ] || echo "Aviso: define ANDROID_SERIAL en tools/dev.local.sh" >&2
export MSYS_NO_PATHCONV=1
A="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
GRADLE="$LOCALAPPDATA/wf-tools/gradle-8.9/bin/gradle"
PROJECT="$(cd "$_DEV_DIR/.." && pwd)"
# Capturas y otros ficheros temporales (fuera del proyecto).
SP="${TMP:-/tmp}/wallpaper-editor-dev"
mkdir -p "$SP"

adb() { "$A" -s "$ANDROID_SERIAL" "$@"; }

# Captura de pantalla en $SP/<nombre>.png (no toca nada).
shot() { adb exec-out screencap -p > "$SP/${1:-s}.png"; }

# Compila (errores de Kotlin únicamente) e instala SIN cerrar la app: cerrarla quita el fondo dinámico.
bi() {
  (cd "$PROJECT" && "$GRADLE" assembleRelease --console=plain -q 2>&1 | grep -aE "^e:|went wrong" | head -20)
  adb install -r "$PROJECT/app/build/outputs/apk/release/app-release.apk" | tail -1
}

# Qué hay delante: el escritorio o nuestra app.
onhome() { adb shell dumpsys window | grep mCurrentFocus | grep -qiE "launcher"; }
inapp() { adb shell dumpsys window | grep mCurrentFocus | grep -q "com.wallpaperfit.app"; }

# Toques "protegidos": solo si lo esperado está delante (si no, abortan sin tocar nada).
dtap() { onhome || { echo "NO ESCRITORIO, abortado"; return 1; }; adb shell "cmd input tap $1 $2; sleep ${3:-0.25}; cmd input tap $1 $2"; }
atap() { inapp || { echo "APP NO DELANTE, abortado"; return 1; }; adb shell input tap "$1" "$2"; }
