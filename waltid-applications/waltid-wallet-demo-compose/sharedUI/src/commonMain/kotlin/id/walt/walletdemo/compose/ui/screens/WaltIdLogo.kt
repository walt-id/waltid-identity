package id.walt.walletdemo.compose.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Exact code-native equivalent of the Portal walt.id mark, independent of runtime resources. */
internal val WaltIdLogo: ImageVector by lazy {
    ImageVector.Builder(
        name = "WaltIdLogo",
        defaultWidth = 30.dp,
        defaultHeight = 30.dp,
        viewportWidth = 3940f,
        viewportHeight = 3920f,
    ).apply {
        addGroup(scaleY = -1f, translationY = 3920f)
        addPath(
            pathData = PathParser().parsePathString(LOGO_PATH).toNodes(),
            fill = SolidColor(Color.Black),
        )
        addPath(
            pathData = PathParser().parsePathString(LOGO_DETAIL_PATH).toNodes(),
            fill = SolidColor(Color.Black),
        )
        clearGroup()
    }.build()
}

private const val LOGO_PATH = "M1740 3860c-653-91-1207-490-1485-1072-174-365-232-793-160-1175 115-609 512-1125 1063-1385 448-212 957-243 1424-88 1002 334 1546 1412 1217 2414-191 583-644 1033-1233 1227-185 61-310 80-546 84-118 2-244 0-280-5zm1059-1491c52-18 67-85 30-133-55-71-175 7-137 89 10 22 31 38 74 55 1 0 16-5 33-11zm-811-376 2-343h-140v338c0 186 3 342 7 346 4 4 34 6 68 4l60-3 3-342zm1440 0 2-343h-71c-62 0-71 2-76 20-5 20-5 20-34-1-64-45-193-33-250 25-52 51-73 110-73 201-1 96 14 144 62 197 49 54 80 68 155 68 52 0 71-5 101-25l36-24v108c0 78 4 111 13 115 6 2 39 4 72 3l60-2 3-342zm-1160 225 3-68h50 50l-3-62-3-63-47-3-48-3v-108c0-125 9-148 57-138 30 6 31 4 42-38 6-24 11-50 11-58 0-34-122-49-185-23-56 24-65 55-65 221v145h-40-40v130h40 40v70 71l68-3 67-3 3-67zm-632-77c90-41 106-91 102-316l-3-170-68-3c-53-2-69 0-73 12-5 14-9 14-36 0-84-44-205-23-256 44-23 30-27 45-27 98 0 68 15 98 69 138 37 28 164 30 214 3l32-18v35c0 75-90 99-189 50-28-15-53-25-55-23-13 18-46 82-46 89 0 12 64 53 105 68 51 18 184 14 231-7zm-1003-61c10-36 28-103 41-150l23-84 15 44c9 25 31 92 50 150l34 105h129l28-90c16-49 35-104 43-122 8-17 14-38 14-46 0-9 4-18 9-21 9-6 21 30 51 151 34 140 29 133 110 133 38 0 70-3 70-7 0-16-122-425-139-466-11-27-12-28-82-25l-72 3-44 135c-23 74-43 141-43 148 0 6-4 12-9 12-4 0-20-44-34-97-15-54-35-120-46-148l-19-50-75-3c-74-3-75-3-81 25-7 29-43 150-102 336-19 60-34 115-34 123 0 12 14 14 72 12l73-3 18-65zm2207-180v-250h-140v500h140v-250zm-268-108c55-50 34-134-36-147-47-9-74 1-97 38-27 45-24 64 15 103 42 42 75 44 118 6z"

private const val LOGO_DETAIL_PATH = "M3130 2017c-31-15-60-71-60-115 0-44 23-96 51-116 33-23 100-21 133 5 25 19 26 25 26 114v93l-34 16c-41 19-82 20-116 3zM1450 1857c-30-15-40-76-16-96 24-20 79-24 119-7 33 13 37 19 37 51 0 32-4 38-35 51-42 17-72 18-105 1z"
