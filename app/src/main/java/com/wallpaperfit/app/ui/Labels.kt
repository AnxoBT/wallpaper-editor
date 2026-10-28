package com.wallpaperfit.app.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Details
import androidx.compose.material.icons.outlined.Flare
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.HdrOn
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PhotoFilter
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vignette
import androidx.compose.ui.graphics.vector.ImageVector
import com.wallpaperfit.app.R
import com.wallpaperfit.app.model.AdjustParam
import com.wallpaperfit.app.model.BrushEffect
import com.wallpaperfit.app.model.CurveChannel
import com.wallpaperfit.app.model.CurvePreset
import com.wallpaperfit.app.model.LensShape
import com.wallpaperfit.app.model.ToolGroup
import com.wallpaperfit.app.model.CropAspect
import com.wallpaperfit.app.model.ExpandFill
import com.wallpaperfit.app.model.ExportFormat
import com.wallpaperfit.app.model.FillDetail
import com.wallpaperfit.app.model.FilterPreset
import com.wallpaperfit.app.model.FrameFill
import com.wallpaperfit.app.model.HealEngine
import com.wallpaperfit.app.model.HealShape
import com.wallpaperfit.app.model.ResolutionPreset
import com.wallpaperfit.app.model.Tool
import kotlin.math.roundToInt

val Tool.icon: ImageVector
    get() = when (this) {
        Tool.FIT -> Icons.Outlined.CropFree
        Tool.CROP -> Icons.Outlined.Crop
        Tool.EXPAND -> Icons.Outlined.OpenInFull
        Tool.HEAL -> Icons.Outlined.Healing
        Tool.LAYERS -> Icons.Outlined.Layers
        Tool.TUNE -> Icons.Outlined.Tune
        Tool.CURVES -> Icons.Outlined.ShowChart
        Tool.DETAILS -> Icons.Outlined.Details
        Tool.BRUSH -> Icons.Outlined.Brush
        Tool.FILTERS -> Icons.Outlined.PhotoFilter
        Tool.LENS_BLUR -> Icons.Outlined.BlurOn
        Tool.GLAMOUR -> Icons.Outlined.Flare
        Tool.HDR -> Icons.Outlined.HdrOn
        Tool.DRAMA -> Icons.Outlined.TheaterComedy
        Tool.VIGNETTE -> Icons.Outlined.Vignette
        Tool.GRAIN -> Icons.Outlined.Grain
    }

@get:StringRes
val Tool.label: Int
    get() = when (this) {
        Tool.FIT -> R.string.tool_fit
        Tool.CROP -> R.string.tool_crop
        Tool.EXPAND -> R.string.tool_expand
        Tool.HEAL -> R.string.tool_heal
        Tool.LAYERS -> R.string.tool_layers
        Tool.TUNE -> R.string.tool_tune
        Tool.CURVES -> R.string.tool_curves
        Tool.DETAILS -> R.string.tool_details
        Tool.BRUSH -> R.string.tool_brush
        Tool.FILTERS -> R.string.tool_filters
        Tool.LENS_BLUR -> R.string.tool_lens_blur
        Tool.GLAMOUR -> R.string.tool_glamour
        Tool.HDR -> R.string.tool_hdr
        Tool.DRAMA -> R.string.tool_drama
        Tool.VIGNETTE -> R.string.tool_vignette
        Tool.GRAIN -> R.string.tool_grain
    }

@get:StringRes
val ToolGroup.label: Int
    get() = when (this) {
        ToolGroup.COMPOSE -> R.string.group_compose
        ToolGroup.ADJUST -> R.string.group_adjust
        ToolGroup.EFFECTS -> R.string.group_effects
    }

@get:StringRes
val AdjustParam.label: Int
    get() = when (this) {
        AdjustParam.BRIGHTNESS -> R.string.param_brightness
        AdjustParam.CONTRAST -> R.string.param_contrast
        AdjustParam.SATURATION -> R.string.param_saturation
        AdjustParam.AMBIANCE -> R.string.param_ambiance
        AdjustParam.HIGHLIGHTS -> R.string.param_highlights
        AdjustParam.SHADOWS -> R.string.param_shadows
        AdjustParam.WARMTH -> R.string.param_warmth
        AdjustParam.TINT -> R.string.param_tint
        AdjustParam.STRUCTURE -> R.string.param_structure
        AdjustParam.SHARPEN -> R.string.param_sharpen
        AdjustParam.DEHAZE -> R.string.param_dehaze
        AdjustParam.LENS_STRENGTH -> R.string.param_lens_strength
        AdjustParam.LENS_TRANSITION -> R.string.param_lens_transition
        AdjustParam.LENS_VIGNETTE -> R.string.param_lens_vignette
        AdjustParam.GLOW -> R.string.param_glow
        AdjustParam.GLOW_SATURATION -> R.string.param_saturation
        AdjustParam.GLOW_WARMTH -> R.string.param_warmth
        AdjustParam.HDR_STRENGTH -> R.string.param_filter_strength
        AdjustParam.HDR_BRIGHTNESS -> R.string.param_brightness
        AdjustParam.HDR_SATURATION -> R.string.param_saturation
        AdjustParam.DRAMA_STRENGTH -> R.string.param_filter_strength
        AdjustParam.DRAMA_SATURATION -> R.string.param_saturation
        AdjustParam.VIGNETTE -> R.string.param_vignette
        AdjustParam.VIGNETTE_SIZE -> R.string.param_vignette_size
        AdjustParam.GRAIN -> R.string.param_grain
        AdjustParam.GRAIN_SIZE -> R.string.param_grain_size
    }

/** Valor tal y como lo muestra Snapseed: con signo en los parámetros bipolares. */
fun AdjustParam.format(value: Float): String {
    val rounded = value.roundToInt()
    return if (isSigned && rounded > 0) "+$rounded" else rounded.toString()
}

@get:StringRes
val FrameFill.label: Int
    get() = when (this) {
        FrameFill.BLUR -> R.string.fill_blur
        FrameFill.WHITE -> R.string.fill_white
        FrameFill.BLACK -> R.string.fill_black
    }

@get:StringRes
val ResolutionPreset.label: Int
    get() = when (this) {
        ResolutionPreset.FHD_PLUS_2340 -> R.string.preset_fhd_plus
        ResolutionPreset.QHD_PLUS -> R.string.preset_qhd_plus
        ResolutionPreset.FHD_PORTRAIT -> R.string.preset_fhd_portrait
        ResolutionPreset.TABLET -> R.string.preset_tablet
        ResolutionPreset.SQUARE -> R.string.preset_square
        ResolutionPreset.PC_FHD -> R.string.preset_pc_fhd
        ResolutionPreset.PC_QHD -> R.string.preset_pc_qhd
    }

@get:StringRes
val FilterPreset.label: Int
    get() = when (this) {
        FilterPreset.NONE -> R.string.filter_none
        FilterPreset.BW -> R.string.filter_bw
        FilterPreset.NOIR -> R.string.filter_noir
        FilterPreset.SEPIA -> R.string.filter_sepia
        FilterPreset.INVERT -> R.string.filter_invert
        FilterPreset.VINTAGE -> R.string.filter_vintage
        FilterPreset.WARM -> R.string.filter_warm
        FilterPreset.COOL -> R.string.filter_cool
        FilterPreset.DRAMA -> R.string.filter_drama
        FilterPreset.VIVID -> R.string.filter_vivid
        FilterPreset.MATTE -> R.string.filter_matte
        FilterPreset.CINEMA -> R.string.filter_cinema
    }

@get:StringRes
val ExportFormat.label: Int
    get() = when (this) {
        ExportFormat.PNG -> R.string.format_png
        ExportFormat.JPEG -> R.string.format_jpeg
    }

@get:StringRes
val ExpandFill.label: Int
    get() = when (this) {
        ExpandFill.SMART -> R.string.fill_smart
        ExpandFill.WHITE -> R.string.fill_white
        ExpandFill.BLACK -> R.string.fill_black
    }

@get:StringRes
val CropAspect.label: Int
    get() = when (this) {
        CropAspect.FREE -> R.string.crop_free
        CropAspect.ORIGINAL -> R.string.crop_original
        CropAspect.SQUARE -> R.string.crop_square
        CropAspect.PORTRAIT_4_5 -> R.string.crop_4_5
        CropAspect.PORTRAIT_3_4 -> R.string.crop_3_4
        CropAspect.PORTRAIT_9_16 -> R.string.crop_9_16
        CropAspect.SCREEN -> R.string.crop_screen
        CropAspect.LANDSCAPE_16_9 -> R.string.crop_16_9
    }

@get:StringRes
val FillDetail.label: Int
    get() = when (this) {
        FillDetail.CLEAN -> R.string.detail_clean
        FillDetail.TEXTURE -> R.string.detail_texture
    }

@get:StringRes
val HealShape.label: Int
    get() = when (this) {
        HealShape.STROKE -> R.string.heal_shape_brush
        HealShape.RECT, HealShape.TEXT -> R.string.heal_shape_rect
    }

@get:StringRes
val HealEngine.label: Int
    get() = when (this) {
        HealEngine.AI -> R.string.heal_engine_ai
        HealEngine.CLASSIC -> R.string.heal_engine_classic
        HealEngine.MIX -> R.string.heal_engine_mix
        HealEngine.SHUFFLE -> R.string.heal_engine_shuffle
    }

@get:StringRes
val FillDetail.description: Int
    get() = when (this) {
        FillDetail.CLEAN -> R.string.detail_clean_desc
        FillDetail.TEXTURE -> R.string.detail_texture_desc
    }

@get:StringRes
val CurveChannel.label: Int
    get() = when (this) {
        CurveChannel.RGB -> R.string.curve_rgb
        CurveChannel.RED -> R.string.curve_red
        CurveChannel.GREEN -> R.string.curve_green
        CurveChannel.BLUE -> R.string.curve_blue
    }

@get:StringRes
val CurvePreset.label: Int
    get() = when (this) {
        CurvePreset.LINEAR -> R.string.curve_linear
        CurvePreset.SOFT_CONTRAST -> R.string.curve_soft_contrast
        CurvePreset.STRONG_CONTRAST -> R.string.curve_strong_contrast
        CurvePreset.BRIGHTEN -> R.string.curve_brighten
        CurvePreset.DARKEN -> R.string.curve_darken
        CurvePreset.MATTE -> R.string.curve_matte
        CurvePreset.FADED -> R.string.curve_faded
    }

@get:StringRes
val BrushEffect.label: Int
    get() = when (this) {
        BrushEffect.EXPOSURE -> R.string.brush_exposure
        BrushEffect.TEMPERATURE -> R.string.brush_temperature
        BrushEffect.SATURATION -> R.string.brush_saturation
        BrushEffect.STRUCTURE -> R.string.brush_structure
    }

@get:StringRes
val LensShape.label: Int
    get() = when (this) {
        LensShape.RADIAL -> R.string.lens_radial
        LensShape.LINEAR -> R.string.lens_linear
    }
