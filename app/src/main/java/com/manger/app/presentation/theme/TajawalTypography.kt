package com.manger.app.presentation

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.manger.app.R

  
private val tajawal = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.Bold),
    Font(R.font.tajawal_extra_bold, FontWeight.ExtraBold)
)
private val material = Typography()
internal val MangerTypography = Typography(
    displayLarge = material.displayLarge.copy(fontFamily = tajawal),
    displayMedium = material.displayMedium.copy(fontFamily = tajawal),
    displaySmall = material.displaySmall.copy(fontFamily = tajawal),
    headlineLarge = material.headlineLarge.copy(fontFamily = tajawal),
    headlineMedium = material.headlineMedium.copy(fontFamily = tajawal),
    headlineSmall = material.headlineSmall.copy(fontFamily = tajawal),
    titleLarge = material.titleLarge.copy(fontFamily = tajawal),
    titleMedium = material.titleMedium.copy(fontFamily = tajawal),
    titleSmall = material.titleSmall.copy(fontFamily = tajawal),
    bodyLarge = material.bodyLarge.copy(fontFamily = tajawal),
    bodyMedium = material.bodyMedium.copy(fontFamily = tajawal),
    bodySmall = material.bodySmall.copy(fontFamily = tajawal),
    labelLarge = material.labelLarge.copy(fontFamily = tajawal),
    labelMedium = material.labelMedium.copy(fontFamily = tajawal),
    labelSmall = material.labelSmall.copy(fontFamily = tajawal)
)
