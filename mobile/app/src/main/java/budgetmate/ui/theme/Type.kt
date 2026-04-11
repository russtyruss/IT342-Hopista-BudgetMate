package budgetmate.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

import androidx.compose.material3.Typography

val Typography = Typography(
	headlineSmall = TextStyle(
		fontFamily = FontFamily.SansSerif,
		fontWeight = FontWeight.Bold,
		fontSize = 30.sp,
		lineHeight = 36.sp,
		letterSpacing = 0.sp
	),
	titleLarge = TextStyle(
		fontFamily = FontFamily.SansSerif,
		fontWeight = FontWeight.SemiBold,
		fontSize = 24.sp,
		lineHeight = 30.sp,
		letterSpacing = 0.sp
	),
	titleMedium = TextStyle(
		fontFamily = FontFamily.SansSerif,
		fontWeight = FontWeight.SemiBold,
		fontSize = 18.sp,
		lineHeight = 24.sp,
		letterSpacing = 0.15.sp
	),
	bodyLarge = TextStyle(
		fontFamily = FontFamily.SansSerif,
		fontWeight = FontWeight.Normal,
		fontSize = 16.sp,
		lineHeight = 24.sp,
		letterSpacing = 0.15.sp
	),
	bodyMedium = TextStyle(
		fontFamily = FontFamily.SansSerif,
		fontWeight = FontWeight.Normal,
		fontSize = 14.sp,
		lineHeight = 20.sp,
		letterSpacing = 0.1.sp
	),
	labelLarge = TextStyle(
		fontFamily = FontFamily.SansSerif,
		fontWeight = FontWeight.Medium,
		fontSize = 14.sp,
		lineHeight = 20.sp,
		letterSpacing = 0.1.sp
	)
)
