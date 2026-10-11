package com.manger.app.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manger.app.domain.RosterNumbers

/** SERVER: Feed merged, authorized local + remote suggestions only after authenticated synchronization. */
@Composable
internal fun KnownRecordField(
    value: String,
    onValueChange: (String) -> Unit,
    title: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    requireKnown: Boolean = false,
    allowBlank: Boolean = false,
    maxSuggestions: Int = 6
) {
    var expanded by remember { mutableStateOf(false) }
    val normalizedOptions = remember(options) { options.map(String::trim).filter(String::isNotEmpty).distinct() }
    val valid = value.trim() in normalizedOptions
    val matches = remember(value, normalizedOptions, maxSuggestions) {
        val query = RosterNumbers.searchKey(value)
        if (query.isBlank()) normalizedOptions.take(maxSuggestions)
        else normalizedOptions.asSequence()
            .filter { RosterNumbers.searchKey(it).contains(query) && it != value.trim() }
            .take(maxSuggestions).toList()
    }
    Column(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it); expanded = true },
            label = { Text(title) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) expanded = true },
            isError = requireKnown && value.isNotBlank() && !valid,
            trailingIcon = { if (requireKnown && valid) Text("✓") }
        )
        if (expanded && matches.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.small, tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().heightIn(max = 190.dp).verticalScroll(rememberScrollState())) {
                    matches.forEach { suggestion ->
                        Text(suggestion,
                            modifier = Modifier.fillMaxWidth().clickable {
                                onValueChange(suggestion); expanded = false
                            }.padding(horizontal = 14.dp, vertical = 9.dp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (requireKnown && !valid && (!allowBlank || value.isNotBlank())) {
            Text("اختر موقعاً مسجلاً في إدارة المواقع", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}
