package com.youfeng.sfs.ctinstaller.ui.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun RadioOptionItem(
    title: String,
    summary: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
    normal: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }

    ListItem(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .toggleable(
                value = selected,
                interactionSource = interactionSource,
                role = Role.RadioButton,
                enabled = summary == null || normal,
                indication = LocalIndication.current,
                onValueChange = { _ -> onClick() }
            ),
        headlineContent = {
            Text(title)
        },
        leadingContent = {
            RadioButton(
                selected = selected,
                enabled = summary == null || normal,
                onClick = onClick,
                interactionSource = interactionSource
            )
        },
        supportingContent = summary?.let {
            {
                if (normal)
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium
                    )
                else
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
            }
        },
        colors = ListItemDefaults.colors(containerColor = AlertDialogDefaults.containerColor)
    )
}
