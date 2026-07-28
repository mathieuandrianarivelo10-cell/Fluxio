package com.fluxio.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Composable
fun CustomPauseIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Row(
        modifier = modifier.size(13.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(tint, RoundedCornerShape(1.dp))
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(tint, RoundedCornerShape(1.dp))
        )
    }
}

@Composable
fun CustomFullscreenIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(14.dp)) {
        val scaleX = size.width / 640f
        val scaleY = size.height / 640f
        val path = androidx.compose.ui.graphics.Path().apply {
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
            moveTo(512f * scaleX, 112f * scaleY)
            cubicTo(520.8f * scaleX, 112f * scaleY, 528f * scaleX, 119.2f * scaleY, 528f * scaleX, 128f * scaleY)
            lineTo(528f * scaleX, 512f * scaleY)
            cubicTo(528f * scaleX, 520.8f * scaleY, 520.8f * scaleX, 528f * scaleY, 512f * scaleX, 528f * scaleY)
            lineTo(128f * scaleX, 528f * scaleY)
            cubicTo(119.2f * scaleX, 528f * scaleY, 112f * scaleX, 520.8f * scaleY, 112f * scaleX, 512f * scaleY)
            lineTo(112f * scaleX, 128f * scaleY)
            cubicTo(112f * scaleX, 119.2f * scaleY, 119.2f * scaleX, 112f * scaleY, 128f * scaleX, 112f * scaleY)
            lineTo(512f * scaleX, 112f * scaleY)
            close()

            moveTo(128f * scaleX, 64f * scaleY)
            cubicTo(92.7f * scaleX, 64f * scaleY, 64f * scaleX, 92.7f * scaleY, 64f * scaleX, 128f * scaleY)
            lineTo(64f * scaleX, 512f * scaleY)
            cubicTo(64f * scaleX, 547.3f * scaleY, 92.7f * scaleX, 576f * scaleY, 128f * scaleX, 576f * scaleY)
            lineTo(512f * scaleX, 576f * scaleY)
            cubicTo(547.3f * scaleX, 576f * scaleY, 576f * scaleX, 547.3f * scaleY, 576f * scaleX, 512f * scaleY)
            lineTo(576f * scaleX, 128f * scaleY)
            cubicTo(576f * scaleX, 92.7f * scaleY, 547.3f * scaleX, 64f * scaleY, 512f * scaleX, 64f * scaleY)
            lineTo(128f * scaleX, 64f * scaleY)
            close()
        }
        drawPath(
            path = path,
            color = tint,
            style = androidx.compose.ui.graphics.drawscope.Fill
        )
    }
}

@Composable
fun CustomReduceIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(14.dp)) {
        val scaleX = size.width / 640f
        val scaleY = size.height / 640f
        val path = androidx.compose.ui.graphics.Path().apply {
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
            moveTo(256f * scaleX, 128f * scaleY)
            cubicTo(256f * scaleX, 110.3f * scaleY, 241.7f * scaleX, 96f * scaleY, 224f * scaleX, 96f * scaleY)
            cubicTo(206.3f * scaleX, 96f * scaleY, 192f * scaleX, 110.3f * scaleY, 192f * scaleX, 128f * scaleY)
            lineTo(192f * scaleX, 192f * scaleY)
            lineTo(128f * scaleX, 192f * scaleY)
            cubicTo(110.3f * scaleX, 192f * scaleY, 96f * scaleX, 206.3f * scaleY, 96f * scaleX, 224f * scaleY)
            cubicTo(96f * scaleX, 241.7f * scaleY, 110.3f * scaleX, 256f * scaleY, 128f * scaleX, 256f * scaleY)
            lineTo(224f * scaleX, 256f * scaleY)
            cubicTo(241.7f * scaleX, 256f * scaleY, 256f * scaleX, 241.7f * scaleY, 256f * scaleX, 224f * scaleY)
            lineTo(256f * scaleX, 128f * scaleY)
            close()

            moveTo(128f * scaleX, 384f * scaleY)
            cubicTo(110.3f * scaleX, 384f * scaleY, 96f * scaleX, 398.3f * scaleY, 96f * scaleX, 416f * scaleY)
            cubicTo(96f * scaleX, 433.7f * scaleY, 110.3f * scaleX, 448f * scaleY, 128f * scaleX, 448f * scaleY)
            lineTo(192f * scaleX, 448f * scaleY)
            lineTo(192f * scaleX, 512f * scaleY)
            cubicTo(192f * scaleX, 529.7f * scaleY, 206.3f * scaleX, 544f * scaleY, 224f * scaleX, 544f * scaleY)
            cubicTo(241.7f * scaleX, 544f * scaleY, 256f * scaleX, 529.7f * scaleY, 256f * scaleX, 512f * scaleY)
            lineTo(256f * scaleX, 416f * scaleY)
            cubicTo(256f * scaleX, 398.3f * scaleY, 241.7f * scaleX, 384f * scaleY, 224f * scaleX, 384f * scaleY)
            lineTo(128f * scaleX, 384f * scaleY)
            close()

            moveTo(448f * scaleX, 128f * scaleY)
            cubicTo(448f * scaleX, 110.3f * scaleY, 433.7f * scaleX, 96f * scaleY, 416f * scaleX, 96f * scaleY)
            cubicTo(398.3f * scaleX, 96f * scaleY, 384f * scaleX, 110.3f * scaleY, 384f * scaleX, 128f * scaleY)
            lineTo(384f * scaleX, 224f * scaleY)
            cubicTo(384f * scaleX, 241.7f * scaleY, 398.3f * scaleX, 256f * scaleY, 416f * scaleX, 256f * scaleY)
            lineTo(512f * scaleX, 256f * scaleY)
            cubicTo(529.7f * scaleX, 256f * scaleY, 544f * scaleX, 241.7f * scaleY, 544f * scaleX, 224f * scaleY)
            cubicTo(544f * scaleX, 206.3f * scaleY, 529.7f * scaleX, 192f * scaleY, 512f * scaleX, 192f * scaleY)
            lineTo(448f * scaleX, 192f * scaleY)
            lineTo(448f * scaleX, 128f * scaleY)
            close()

            moveTo(416f * scaleX, 384f * scaleY)
            cubicTo(398.3f * scaleX, 384f * scaleY, 384f * scaleX, 398.3f * scaleY, 384f * scaleX, 416f * scaleY)
            lineTo(384f * scaleX, 512f * scaleY)
            cubicTo(384f * scaleX, 529.7f * scaleY, 398.3f * scaleX, 544f * scaleY, 416f * scaleX, 544f * scaleY)
            cubicTo(433.7f * scaleX, 544f * scaleY, 448f * scaleX, 529.7f * scaleY, 448f * scaleX, 512f * scaleY)
            lineTo(448f * scaleX, 448f * scaleY)
            lineTo(512f * scaleX, 448f * scaleY)
            cubicTo(529.7f * scaleX, 448f * scaleY, 544f * scaleX, 433.7f * scaleY, 544f * scaleX, 416f * scaleY)
            cubicTo(544f * scaleX, 398.3f * scaleY, 529.7f * scaleX, 384f * scaleY, 512f * scaleX, 384f * scaleY)
            lineTo(416f * scaleX, 384f * scaleY)
            close()
        }
        drawPath(
            path = path,
            color = tint,
            style = androidx.compose.ui.graphics.drawscope.Fill
        )
    }
}

val TvActive: ImageVector
    get() {
        if (_tvActive != null) return _tvActive!!
        _tvActive = ImageVector.Builder(
            name = "tv_active",
            defaultWidth = 40.dp,
            defaultHeight = 40.dp,
            viewportWidth = 40f,
            viewportHeight = 40f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.Companion.NonZero
            ) {
                moveTo(9.26f, 31.67f)
                lineTo(8.29f, 34.58f)
                quadTo(8.21f, 34.78f, 8.07f, 34.89f)
                reflectiveQuadTo(7.72f, 35f)
                horizontalLineTo(7.56f)
                quadTo(7.31f, 35f, 7.13f, 34.82f)
                reflectiveQuadTo(6.94f, 34.39f)
                verticalLineTo(31.67f)
                horizontalLineTo(6.11f)
                quadToRelative(-1.15f, 0f, -1.97f, -0.81f)
                reflectiveQuadTo(3.33f, 28.89f)
                verticalLineTo(9.44f)
                quadToRelative(0f, -1.15f, 0.81f, -1.97f)
                reflectiveQuadTo(6.11f, 6.67f)
                horizontalLineTo(33.89f)
                quadToRelative(1.15f, 0f, 1.97f, 0.81f)
                reflectiveQuadToRelative(0.81f, 1.97f)
                verticalLineTo(28.89f)
                quadToRelative(0f, 1.15f, -0.82f, 1.96f)
                reflectiveQuadToRelative(-1.97f, 0.81f)
                horizontalLineTo(33.06f)
                verticalLineToRelative(2.76f)
                quadToRelative(0f, 0.25f, -0.16f, 0.41f)
                reflectiveQuadTo(32.49f, 35f)
                horizontalLineTo(32.28f)
                quadToRelative(-0.19f, 0f, -0.33f, -0.1f)
                reflectiveQuadTo(31.75f, 34.6f)
                lineTo(30.78f, 31.67f)
                horizontalLineTo(9.26f)
                close()
            }
        }.build()
        return _tvActive!!
    }

private var _tvActive: ImageVector? = null

val TvInactive: ImageVector
    get() {
        if (_tvInactive != null) return _tvInactive!!
        _tvInactive = ImageVector.Builder(
            name = "tv_inactive",
            defaultWidth = 40.dp,
            defaultHeight = 40.dp,
            viewportWidth = 40f,
            viewportHeight = 40f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.Companion.NonZero
            ) {
                moveTo(9.26f, 31.67f)
                lineTo(8.29f, 34.58f)
                quadTo(8.21f, 34.78f, 8.07f, 34.89f)
                reflectiveQuadTo(7.73f, 35f)
                horizontalLineTo(7.56f)
                quadTo(7.31f, 35f, 7.13f, 34.82f)
                reflectiveQuadTo(6.94f, 34.39f)
                verticalLineTo(31.67f)
                horizontalLineTo(6.11f)
                quadToRelative(-1.15f, 0f, -1.96f, -0.82f)
                reflectiveQuadTo(3.33f, 28.89f)
                verticalLineTo(9.44f)
                quadTo(3.33f, 8.3f, 4.15f, 7.48f)
                reflectiveQuadTo(6.11f, 6.67f)
                horizontalLineTo(33.89f)
                quadToRelative(1.15f, 0f, 1.96f, 0.82f)
                reflectiveQuadToRelative(0.82f, 1.96f)
                verticalLineTo(28.89f)
                quadToRelative(0f, 1.15f, -0.82f, 1.96f)
                reflectiveQuadToRelative(-1.96f, 0.82f)
                horizontalLineTo(33.06f)
                verticalLineToRelative(2.76f)
                quadToRelative(0f, 0.24f, -0.16f, 0.4f)
                reflectiveQuadTo(32.49f, 35f)
                horizontalLineTo(32.28f)
                quadToRelative(-0.19f, 0f, -0.33f, -0.11f)
                reflectiveQuadTo(31.75f, 34.6f)
                lineTo(30.78f, 31.67f)
                horizontalLineTo(9.26f)
                close()
                moveTo(6.11f, 28.89f)
                horizontalLineTo(33.89f)
                verticalLineTo(9.44f)
                horizontalLineTo(6.11f)
                verticalLineTo(28.89f)
                close()
                moveTo(20f, 19.17f)
                close()
            }
        }.build()
        return _tvInactive!!
    }

private var _tvInactive: ImageVector? = null

@Composable
fun CustomPlayMenuIcon(selected: Boolean, modifier: Modifier = Modifier, tint: Color = Color.White) {
    androidx.compose.material3.Icon(
        imageVector = if (selected) TvActive else TvInactive,
        contentDescription = null,
        tint = tint,
        modifier = modifier
    )
}

val FamilyStarActive: ImageVector
    get() {
        if (_familyStarActive != null) return _familyStarActive!!
        _familyStarActive = ImageVector.Builder(
            name = "family_star_active",
            defaultWidth = 40.dp,
            defaultHeight = 40.dp,
            viewportWidth = 40f,
            viewportHeight = 40f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.Companion.NonZero
            ) {
                moveTo(23.56f, 25.48f)
                quadToRelative(1.56f, -1.19f, 2.27f, -3.06f)
                horizontalLineTo(14.17f)
                quadToRelative(0.71f, 1.88f, 2.27f, 3.06f)
                reflectiveQuadTo(20f, 26.67f)
                reflectiveQuadToRelative(3.56f, -1.19f)
                close()
                moveTo(17.6f, 19.27f)
                quadToRelative(0.73f, -0.73f, 0.73f, -1.77f)
                reflectiveQuadTo(17.6f, 15.73f)
                quadTo(16.88f, 15f, 15.83f, 15f)
                reflectiveQuadToRelative(-1.77f, 0.73f)
                reflectiveQuadTo(13.33f, 17.5f)
                reflectiveQuadToRelative(0.73f, 1.77f)
                reflectiveQuadTo(15.83f, 20f)
                reflectiveQuadTo(17.6f, 19.27f)
                close()
                moveToRelative(8.33f, 0f)
                quadToRelative(0.73f, -0.73f, 0.73f, -1.77f)
                reflectiveQuadTo(25.94f, 15.73f)
                reflectiveQuadTo(24.17f, 15f)
                quadToRelative(-1.04f, 0f, -1.77f, 0.73f)
                reflectiveQuadTo(21.67f, 17.5f)
                reflectiveQuadToRelative(0.73f, 1.77f)
                reflectiveQuadTo(24.17f, 20f)
                reflectiveQuadToRelative(1.77f, -0.73f)
                close()
                moveTo(12.76f, 10.75f)
                lineTo(17.57f, 4.51f)
                quadTo(18.04f, 3.9f, 18.68f, 3.6f)
                reflectiveQuadTo(20f, 3.31f)
                reflectiveQuadToRelative(1.32f, 0.3f)
                reflectiveQuadToRelative(1.1f, 0.91f)
                lineToRelative(4.81f, 6.24f)
                lineToRelative(7.3f, 2.45f)
                quadToRelative(1f, 0.33f, 1.57f, 1.16f)
                reflectiveQuadToRelative(0.57f, 1.83f)
                quadToRelative(0f, 0.46f, -0.13f, 0.92f)
                reflectiveQuadToRelative(-0.44f, 0.89f)
                lineToRelative(-4.69f, 6.69f)
                lineToRelative(0.17f, 7.08f)
                quadToRelative(0.01f, 1.35f, -0.9f, 2.28f)
                reflectiveQuadTo(28.51f, 35f)
                quadToRelative(-0.08f, 0f, -0.83f, -0.13f)
                lineTo(20f, 32.74f)
                lineToRelative(-7.67f, 2.14f)
                quadToRelative(-0.21f, 0.09f, -0.45f, 0.11f)
                reflectiveQuadTo(11.44f, 35f)
                quadTo(10.18f, 35f, 9.28f, 34.06f)
                reflectiveQuadTo(8.42f, 31.76f)
                lineToRelative(0.17f, -7.1f)
                lineTo(3.9f, 17.99f)
                quadTo(3.6f, 17.56f, 3.47f, 17.1f)
                reflectiveQuadTo(3.33f, 16.17f)
                quadToRelative(0f, -0.99f, 0.56f, -1.8f)
                reflectiveQuadTo(5.46f, 13.21f)
                lineToRelative(7.3f, -2.45f)
                close()
            }
        }.build()
        return _familyStarActive!!
    }

private var _familyStarActive: ImageVector? = null

val FamilyStarInactive: ImageVector
    get() {
        if (_familyStarInactive != null) return _familyStarInactive!!
        _familyStarInactive = ImageVector.Builder(
            name = "family_star_inactive",
            defaultWidth = 40.dp,
            defaultHeight = 40.dp,
            viewportWidth = 40f,
            viewportHeight = 40f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.Companion.NonZero
            ) {
                moveTo(23.56f, 25.48f)
                quadToRelative(1.56f, -1.19f, 2.27f, -3.06f)
                horizontalLineTo(14.17f)
                quadToRelative(0.71f, 1.88f, 2.27f, 3.06f)
                reflectiveQuadTo(20f, 26.67f)
                reflectiveQuadToRelative(3.56f, -1.19f)
                close()
                moveTo(17.6f, 19.27f)
                quadToRelative(0.73f, -0.73f, 0.73f, -1.77f)
                reflectiveQuadTo(17.6f, 15.73f)
                quadTo(16.88f, 15f, 15.83f, 15f)
                reflectiveQuadToRelative(-1.77f, 0.73f)
                reflectiveQuadTo(13.33f, 17.5f)
                reflectiveQuadToRelative(0.73f, 1.77f)
                reflectiveQuadTo(15.83f, 20f)
                reflectiveQuadTo(17.6f, 19.27f)
                close()
                moveToRelative(8.33f, 0f)
                quadToRelative(0.73f, -0.73f, 0.73f, -1.77f)
                reflectiveQuadTo(25.94f, 15.73f)
                reflectiveQuadTo(24.17f, 15f)
                quadToRelative(-1.04f, 0f, -1.77f, 0.73f)
                reflectiveQuadTo(21.67f, 17.5f)
                reflectiveQuadToRelative(0.73f, 1.77f)
                reflectiveQuadTo(24.17f, 20f)
                reflectiveQuadToRelative(1.77f, -0.73f)
                close()
                moveTo(12.76f, 10.75f)
                lineTo(17.57f, 4.51f)
                quadTo(18.04f, 3.9f, 18.68f, 3.6f)
                reflectiveQuadTo(20f, 3.31f)
                reflectiveQuadToRelative(1.32f, 0.3f)
                reflectiveQuadToRelative(1.1f, 0.91f)
                lineToRelative(4.81f, 6.24f)
                lineToRelative(7.3f, 2.45f)
                quadToRelative(1f, 0.33f, 1.57f, 1.16f)
                reflectiveQuadToRelative(0.57f, 1.83f)
                quadToRelative(0f, 0.46f, -0.13f, 0.92f)
                reflectiveQuadToRelative(-0.44f, 0.89f)
                lineToRelative(-4.69f, 6.69f)
                lineToRelative(0.17f, 7.08f)
                quadToRelative(0.01f, 1.35f, -0.9f, 2.28f)
                reflectiveQuadTo(28.51f, 35f)
                quadToRelative(-0.08f, 0f, -0.83f, -0.13f)
                lineTo(20f, 32.74f)
                lineToRelative(-7.67f, 2.14f)
                quadToRelative(-0.21f, 0.09f, -0.45f, 0.11f)
                reflectiveQuadTo(11.44f, 35f)
                quadTo(10.18f, 35f, 9.28f, 34.06f)
                reflectiveQuadTo(8.42f, 31.76f)
                lineToRelative(0.17f, -7.1f)
                lineTo(3.9f, 17.99f)
                quadTo(3.6f, 17.56f, 3.47f, 17.1f)
                reflectiveQuadTo(3.33f, 16.17f)
                quadToRelative(0f, -0.99f, 0.56f, -1.8f)
                reflectiveQuadTo(5.46f, 13.21f)
                lineToRelative(7.3f, -2.45f)
                close()
                moveToRelative(1.69f, 2.4f)
                lineToRelative(-8.5f, 2.83f)
                lineToRelative(5.44f, 7.88f)
                lineToRelative(-0.19f, 8.4f)
                lineTo(20f, 29.83f)
                lineToRelative(8.81f, 2.47f)
                lineTo(28.61f, 23.86f)
                lineToRelative(5.44f, -7.79f)
                lineToRelative(-8.5f, -2.92f)
                lineTo(20f, 5.92f)
                lineToRelative(-5.56f, 7.24f)
                close()
                moveTo(20f, 19.11f)
                close()
            }
        }.build()
        return _familyStarInactive!!
    }

private var _familyStarInactive: ImageVector? = null

@Composable
fun CustomStarMenuIcon(selected: Boolean, modifier: Modifier = Modifier, tint: Color = Color.White) {
    androidx.compose.material3.Icon(
        imageVector = if (selected) FamilyStarActive else FamilyStarInactive,
        contentDescription = null,
        tint = tint,
        modifier = modifier
    )
}

val HomeActive: ImageVector
    get() {
        if (_homeActive != null) return _homeActive!!
        _homeActive = ImageVector.Builder(
            name = "home_active",
            defaultWidth = 40.dp,
            defaultHeight = 40.dp,
            viewportWidth = 40f,
            viewportHeight = 40f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.Companion.NonZero
            ) {
                moveTo(6.67f, 32.22f)
                verticalLineTo(16.39f)
                quadToRelative(0f, -0.66f, 0.3f, -1.25f)
                reflectiveQuadTo(7.78f, 14.17f)
                lineTo(18.33f, 6.25f)
                quadTo(19.06f, 5.69f, 20f, 5.69f)
                reflectiveQuadToRelative(1.67f, 0.56f)
                lineToRelative(10.56f, 7.92f)
                quadToRelative(0.52f, 0.38f, 0.82f, 0.97f)
                reflectiveQuadToRelative(0.3f, 1.25f)
                verticalLineTo(32.22f)
                quadToRelative(0f, 1.15f, -0.82f, 1.96f)
                reflectiveQuadTo(30.56f, 35f)
                horizontalLineTo(24.72f)
                quadToRelative(-0.59f, 0f, -0.99f, -0.4f)
                reflectiveQuadToRelative(-0.4f, -0.99f)
                verticalLineTo(24.72f)
                quadToRelative(0f, -0.59f, -0.4f, -0.99f)
                reflectiveQuadToRelative(-0.99f, -0.4f)
                horizontalLineTo(18.06f)
                quadToRelative(-0.59f, 0f, -0.99f, 0.4f)
                reflectiveQuadToRelative(-0.4f, 0.99f)
                verticalLineToRelative(8.89f)
                quadToRelative(0f, 0.59f, -0.4f, 0.99f)
                reflectiveQuadTo(15.28f, 35f)
                horizontalLineTo(9.44f)
                quadTo(8.3f, 35f, 7.48f, 34.18f)
                reflectiveQuadTo(6.67f, 32.22f)
                close()
            }
        }.build()
        return _homeActive!!
    }

private var _homeActive: ImageVector? = null

val HomeInactive: ImageVector
    get() {
        if (_homeInactive != null) return _homeInactive!!
        _homeInactive = ImageVector.Builder(
            name = "home_inactive",
            defaultWidth = 40.dp,
            defaultHeight = 40.dp,
            viewportWidth = 40f,
            viewportHeight = 40f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.Companion.NonZero
            ) {
                moveTo(9.44f, 32.22f)
                horizontalLineToRelative(5.83f)
                verticalLineTo(23.33f)
                quadToRelative(0f, -0.59f, 0.4f, -0.99f)
                reflectiveQuadToRelative(0.99f, -0.4f)
                horizontalLineToRelative(6.67f)
                quadToRelative(0.59f, 0f, 0.99f, 0.4f)
                reflectiveQuadToRelative(0.4f, 0.99f)
                verticalLineToRelative(8.89f)
                horizontalLineToRelative(5.83f)
                verticalLineTo(16.39f)
                lineTo(20f, 8.47f)
                lineTo(9.44f, 16.39f)
                verticalLineTo(32.22f)
                close()
                moveToRelative(-2.78f, 0f)
                verticalLineTo(16.39f)
                quadToRelative(0f, -0.66f, 0.3f, -1.25f)
                reflectiveQuadTo(7.78f, 14.17f)
                lineTo(18.33f, 6.25f)
                quadTo(19.06f, 5.69f, 20f, 5.69f)
                reflectiveQuadToRelative(1.67f, 0.56f)
                lineToRelative(10.56f, 7.92f)
                quadToRelative(0.52f, 0.38f, 0.82f, 0.97f)
                reflectiveQuadToRelative(0.3f, 1.25f)
                verticalLineTo(32.22f)
                quadToRelative(0f, 1.15f, -0.82f, 1.96f)
                reflectiveQuadTo(30.56f, 35f)
                horizontalLineTo(23.33f)
                quadToRelative(-0.59f, 0f, -0.99f, -0.4f)
                reflectiveQuadToRelative(-0.4f, -0.99f)
                verticalLineTo(24.72f)
                horizontalLineTo(18.06f)
                verticalLineToRelative(8.89f)
                quadToRelative(0f, 0.59f, -0.4f, 0.99f)
                reflectiveQuadTo(16.67f, 35f)
                horizontalLineTo(9.44f)
                quadTo(8.3f, 35f, 7.48f, 34.18f)
                reflectiveQuadTo(6.67f, 32.22f)
                close()
                moveTo(20f, 20.33f)
                close()
            }
        }.build()
        return _homeInactive!!
    }

private var _homeInactive: ImageVector? = null

@Composable
fun CustomHomeMenuIcon(selected: Boolean, modifier: Modifier = Modifier, tint: Color = Color.White) {
    androidx.compose.material3.Icon(
        imageVector = if (selected) HomeActive else HomeInactive,
        contentDescription = null,
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun CustomStreamingMenuIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(24.dp)) {
        val scaleX = size.width / 640f
        val scaleY = size.height / 640f
        val path = androidx.compose.ui.graphics.Path().apply {
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
            moveTo(320f * scaleX, 112f * scaleY)
            cubicTo(434.9f * scaleX, 112f * scaleY, 528f * scaleX, 205.1f * scaleY, 528f * scaleX, 320f * scaleY)
            cubicTo(528f * scaleX, 434.9f * scaleY, 434.9f * scaleX, 528f * scaleY, 320f * scaleX, 528f * scaleY)
            cubicTo(205.1f * scaleX, 528f * scaleY, 112f * scaleX, 434.9f * scaleY, 112f * scaleX, 320f * scaleY)
            cubicTo(112f * scaleX, 205.1f * scaleY, 205.1f * scaleX, 112f * scaleY, 320f * scaleX, 112f * scaleY)
            close()

            moveTo(320f * scaleX, 576f * scaleY)
            cubicTo(461.4f * scaleX, 576f * scaleY, 576f * scaleX, 461.4f * scaleY, 576f * scaleX, 320f * scaleY)
            cubicTo(576f * scaleX, 178.6f * scaleY, 461.4f * scaleX, 64f * scaleY, 320f * scaleX, 64f * scaleY)
            cubicTo(178.6f * scaleX, 64f * scaleY, 64f * scaleX, 178.6f * scaleY, 64f * scaleX, 320f * scaleY)
            cubicTo(64f * scaleX, 461.4f * scaleY, 178.6f * scaleX, 576f * scaleY, 320f * scaleX, 576f * scaleY)
            close()

            moveTo(276.5f * scaleX, 211.5f * scaleY)
            cubicTo(269.1f * scaleX, 207f * scaleY, 259.8f * scaleX, 206.8f * scaleY, 252.2f * scaleX, 211f * scaleY)
            cubicTo(244.6f * scaleX, 215.2f * scaleY, 240f * scaleX, 223.3f * scaleY, 240f * scaleX, 232f * scaleY)
            lineTo(240f * scaleX, 408f * scaleY)
            cubicTo(240f * scaleX, 416.7f * scaleY, 244.7f * scaleX, 424.7f * scaleY, 252.3f * scaleX, 428.9f * scaleY)
            cubicTo(259.9f * scaleX, 433.1f * scaleY, 269.1f * scaleX, 433f * scaleY, 276.6f * scaleX, 428.4f * scaleY)
            lineTo(420.6f * scaleX, 340.4f * scaleY)
            cubicTo(427.7f * scaleX, 336f * scaleY, 432.1f * scaleX, 328.3f * scaleY, 432.1f * scaleX, 319.9f * scaleY)
            cubicTo(432.1f * scaleX, 311.5f * scaleY, 427.7f * scaleX, 303.8f * scaleY, 420.6f * scaleX, 299.4f * scaleY)
            lineTo(276.6f * scaleX, 211.4f * scaleY)
            close()

            moveTo(362f * scaleX, 320f * scaleY)
            lineTo(288f * scaleX, 365.2f * scaleY)
            lineTo(288f * scaleX, 274.8f * scaleY)
            lineTo(362f * scaleX, 320f * scaleY)
            close()
        }
        drawPath(
            path = path,
            color = tint,
            style = androidx.compose.ui.graphics.drawscope.Fill
        )
    }
}

@Composable
fun CustomMaximizeIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(14.dp)) {
        val scaleX = size.width / 640f
        val scaleY = size.height / 640f
        val path = androidx.compose.ui.graphics.Path().apply {
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
            moveTo(512f * scaleX, 112f * scaleY)
            cubicTo(520.8f * scaleX, 112f * scaleY, 528f * scaleX, 119.2f * scaleY, 528f * scaleX, 128f * scaleY)
            lineTo(528f * scaleX, 512f * scaleY)
            cubicTo(528f * scaleX, 520.8f * scaleY, 520.8f * scaleX, 528f * scaleY, 512f * scaleX, 528f * scaleY)
            lineTo(128f * scaleX, 528f * scaleY)
            cubicTo(119.2f * scaleX, 528f * scaleY, 112f * scaleX, 520.8f * scaleY, 112f * scaleX, 512f * scaleY)
            lineTo(112f * scaleX, 128f * scaleY)
            cubicTo(112f * scaleX, 119.2f * scaleY, 119.2f * scaleX, 112f * scaleY, 128f * scaleX, 112f * scaleY)
            lineTo(512f * scaleX, 112f * scaleY)
            close()

            moveTo(128f * scaleX, 64f * scaleY)
            cubicTo(92.7f * scaleX, 64f * scaleY, 64f * scaleX, 92.7f * scaleY, 64f * scaleX, 128f * scaleY)
            lineTo(64f * scaleX, 512f * scaleY)
            cubicTo(64f * scaleX, 547.3f * scaleY, 92.7f * scaleX, 576f * scaleY, 128f * scaleX, 576f * scaleY)
            lineTo(512f * scaleX, 576f * scaleY)
            cubicTo(547.3f * scaleX, 576f * scaleY, 576f * scaleX, 547.3f * scaleY, 576f * scaleX, 512f * scaleY)
            lineTo(576f * scaleX, 128f * scaleY)
            cubicTo(576f * scaleX, 92.7f * scaleY, 547.3f * scaleX, 64f * scaleY, 512f * scaleX, 64f * scaleY)
            lineTo(128f * scaleX, 64f * scaleY)
            close()
        }
        drawPath(
            path = path,
            color = tint,
            style = androidx.compose.ui.graphics.drawscope.Fill
        )
    }
}

@Composable
fun CustomImageImportIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(24.dp)) {
        val scaleX = size.width / 640f
        val scaleY = size.height / 640f
        val path = androidx.compose.ui.graphics.Path().apply {
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
            moveTo(160f * scaleX, 144f * scaleY)
            cubicTo(151.2f * scaleX, 144f * scaleY, 144f * scaleX, 151.2f * scaleY, 144f * scaleX, 160f * scaleY)
            lineTo(144f * scaleX, 480f * scaleY)
            cubicTo(144f * scaleX, 488.8f * scaleY, 151.2f * scaleX, 496f * scaleY, 160f * scaleX, 496f * scaleY)
            lineTo(480f * scaleX, 496f * scaleY)
            cubicTo(488.8f * scaleX, 496f * scaleY, 496f * scaleX, 488.8f * scaleY, 496f * scaleX, 480f * scaleY)
            lineTo(496f * scaleX, 160f * scaleY)
            cubicTo(496f * scaleX, 151.2f * scaleY, 488.8f * scaleX, 144f * scaleY, 480f * scaleX, 144f * scaleY)
            lineTo(160f * scaleX, 144f * scaleY)
            close()

            moveTo(96f * scaleX, 160f * scaleY)
            cubicTo(96f * scaleX, 124.7f * scaleY, 124.7f * scaleX, 96f * scaleY, 160f * scaleX, 96f * scaleY)
            lineTo(480f * scaleX, 96f * scaleY)
            cubicTo(515.3f * scaleX, 96f * scaleY, 544f * scaleX, 124.7f * scaleY, 544f * scaleX, 160f * scaleY)
            lineTo(544f * scaleX, 480f * scaleY)
            cubicTo(544f * scaleX, 515.3f * scaleY, 515.3f * scaleX, 544f * scaleY, 480f * scaleX, 544f * scaleY)
            lineTo(160f * scaleX, 544f * scaleY)
            cubicTo(124.7f * scaleX, 544f * scaleY, 96f * scaleX, 515.3f * scaleY, 96f * scaleX, 480f * scaleY)
            lineTo(96f * scaleX, 160f * scaleY)
            close()

            moveTo(224f * scaleX, 192f * scaleY)
            cubicTo(241.7f * scaleX, 192f * scaleY, 256f * scaleX, 206.3f * scaleY, 256f * scaleX, 224f * scaleY)
            cubicTo(256f * scaleX, 241.7f * scaleY, 241.7f * scaleX, 256f * scaleY, 224f * scaleX, 256f * scaleY)
            cubicTo(206.3f * scaleX, 256f * scaleY, 192f * scaleX, 241.7f * scaleY, 192f * scaleX, 224f * scaleY)
            cubicTo(192f * scaleX, 206.3f * scaleY, 206.3f * scaleX, 192f * scaleY, 224f * scaleX, 192f * scaleY)
            close()

            moveTo(360f * scaleX, 264f * scaleY)
            cubicTo(368.5f * scaleX, 264f * scaleY, 376.4f * scaleX, 268.5f * scaleY, 380.7f * scaleX, 275.8f * scaleY)
            lineTo(460.7f * scaleX, 411.8f * scaleY)
            cubicTo(465.1f * scaleX, 419.2f * scaleY, 465.1f * scaleX, 428.4f * scaleY, 460.8f * scaleX, 435.9f * scaleY)
            cubicTo(456.5f * scaleX, 443.4f * scaleY, 448.6f * scaleX, 448f * scaleY, 440f * scaleX, 448f * scaleY)
            lineTo(200f * scaleX, 448f * scaleY)
            cubicTo(191.1f * scaleX, 448f * scaleY, 182.8f * scaleX, 443f * scaleY, 178.7f * scaleX, 435.1f * scaleY)
            cubicTo(174.6f * scaleX, 427.2f * scaleY, 175.2f * scaleX, 417.6f * scaleY, 180.3f * scaleX, 410.3f * scaleY)
            lineTo(236.3f * scaleX, 330.3f * scaleY)
            cubicTo(240.8f * scaleX, 323.9f * scaleY, 248.1f * scaleX, 320.1f * scaleY, 256f * scaleX, 320.1f * scaleY)
            cubicTo(263.9f * scaleX, 320.1f * scaleY, 271.2f * scaleX, 323.9f * scaleY, 275.7f * scaleX, 330.3f * scaleY)
            lineTo(292.9f * scaleX, 354.9f * scaleY)
            lineTo(339.4f * scaleX, 275.9f * scaleY)
            cubicTo(343.7f * scaleX, 268.6f * scaleY, 351.6f * scaleX, 264.1f * scaleY, 360.1f * scaleX, 264.1f * scaleY)
            close()
        }
        drawPath(
            path = path,
            color = tint,
            style = androidx.compose.ui.graphics.drawscope.Fill
        )
    }
}

@Composable
fun CustomVolumeIcon(isMuted: Boolean, modifier: Modifier = Modifier, tint: Color = Color.White) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.15f, h * 0.35f)
            lineTo(w * 0.4f, h * 0.35f)
            lineTo(w * 0.65f, h * 0.15f)
            lineTo(w * 0.65f, h * 0.85f)
            lineTo(w * 0.4f, h * 0.65f)
            lineTo(w * 0.15f, h * 0.65f)
            close()
        }
        drawPath(path = path, color = tint)
        
        if (isMuted) {
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(w * 0.75f, h * 0.4f),
                end = androidx.compose.ui.geometry.Offset(w * 0.95f, h * 0.6f),
                strokeWidth = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(w * 0.95f, h * 0.4f),
                end = androidx.compose.ui.geometry.Offset(w * 0.75f, h * 0.6f),
                strokeWidth = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        } else {
            drawArc(
                color = tint,
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(w * 0.4f, h * 0.25f),
                size = androidx.compose.ui.geometry.Size(w * 0.4f, h * 0.5f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
            drawArc(
                color = tint,
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(w * 0.25f, h * 0.15f),
                size = androidx.compose.ui.geometry.Size(w * 0.65f, h * 0.7f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
    }
}

