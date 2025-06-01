//package com.xenon.store.compose
//
//import androidx.compose.foundation.background
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.PaddingValues
//import androidx.compose.foundation.layout.RowScope
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material3.ExperimentalMaterial3Api
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.runtime.Composable
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.clip
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.unit.Dp
//import androidx.compose.ui.unit.TextUnit
//import androidx.compose.ui.unit.dp
//import com.xenon.calculator.ui.values.LargeCornerRadius // Keep this if it's your default
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun ActivityScreen(
//    title: @Composable (fontSize: TextUnit, color: Color) -> Unit,
//    navigationIcon: @Composable (() -> Unit)? = null,
//    appBarActions: @Composable RowScope.() -> Unit = {},
//    isAppBarCollapsible: Boolean = true,
//    appBarCollapsedHeight: Dp = 54.dp,
//    appBarExpandedTextColor: Color = MaterialTheme.colorScheme.primary,
//    appBarCollapsedTextColor: Color = MaterialTheme.colorScheme.onBackground,
//    appBarExpandedContainerColor: Color = MaterialTheme.colorScheme.background,
//    appBarCollapsedContainerColor: Color = MaterialTheme.colorScheme.background,
//    appBarNavigationIconContentColor: Color = MaterialTheme.colorScheme.onBackground,
//    appBarActionIconContentColor: Color = MaterialTheme.colorScheme.onBackground,
//    screenBackgroundColor: Color = MaterialTheme.colorScheme.background,
//    contentBackgroundColor: Color = MaterialTheme.colorScheme.surfaceContainer,
//    contentCornerRadius: Dp = LargeCornerRadius,
//
//    contentModifier: Modifier = Modifier,
//    content: @Composable (PaddingValues) -> Unit,
//    dialogs: @Composable () -> Unit = {}
//) {
//    CollapsingAppBarLayout(
//        title = title,
//        navigationIcon = {
//            navigationIcon?.invoke()
//        },
//        actions = appBarActions,
//        expandable = isAppBarCollapsible,
//        collapsedHeight = appBarCollapsedHeight,
//        expandedTextColor = appBarExpandedTextColor,
//        collapsedTextColor = appBarCollapsedTextColor,
//        expandedContainerColor = appBarExpandedContainerColor,
//        collapsedContainerColor = appBarCollapsedContainerColor,
//        navigationIconContentColor = appBarNavigationIconContentColor,
//        actionIconContentColor = appBarActionIconContentColor
//    ) { paddingValuesFromAppBar ->
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .background(screenBackgroundColor)
//                .padding(top = paddingValuesFromAppBar.calculateTopPadding())
//        ) {
//            Column(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .then(contentModifier)
//                    .clip(
//                        RoundedCornerShape(
//                            topStart = contentCornerRadius,
//                            topEnd = contentCornerRadius
//                        )
//                    )
//                    .background(contentBackgroundColor)
//            ) {
//                content(paddingValuesFromAppBar)
//            }
//        }
//        dialogs()
//    }
//}