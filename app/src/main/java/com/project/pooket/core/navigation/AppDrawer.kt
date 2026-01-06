package com.project.pooket.core.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun AppDrawer(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    closeDrawer: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .padding(start = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {

        Surface(
            modifier = Modifier.wrapContentSize(),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(vertical = 24.dp, horizontal = 12.dp)
            ) {
                AppRoute.drawerRoutes.forEach { item ->
                    val isSelected = currentRoute == item.route
                    DrawerIconItem(
                        icon = if (isSelected) item.iconSelected!! else item.iconUnSelected!!,
                        isSelected = isSelected,
                        onClick = {
                            onNavigate(item.route)
                            closeDrawer()
                        }
                    )
                }
            }
        }
    }
}
@Composable
private fun DrawerIconItem(
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                onClick = onClick,
            ),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(24.dp)
        )

        AnimatedVisibility(
            visible = isSelected,
            enter = scaleIn(),
            exit = scaleOut()
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}