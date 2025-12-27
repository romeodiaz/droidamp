package com.droidamp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidamp.ui.theme.AmpDark
import com.droidamp.ui.theme.AmpGreen
import com.droidamp.viewmodel.PlayerViewModel

@Composable
fun SearchScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var wasFocused by remember { mutableStateOf(false) }
    var textFieldValue by remember { 
        mutableStateOf(TextFieldValue(text = uiState.searchQuery)) 
    }
    
    // Select all text when field gains focus
    LaunchedEffect(isFocused) {
        if (isFocused && !wasFocused && textFieldValue.text.isNotEmpty()) {
            textFieldValue = textFieldValue.copy(
                selection = TextRange(0, textFieldValue.text.length)
            )
        }
        wasFocused = isFocused
    }
    
    // Keep text in sync with viewModel when it changes externally
    LaunchedEffect(uiState.searchQuery) {
        if (textFieldValue.text != uiState.searchQuery) {
            textFieldValue = TextFieldValue(
                text = uiState.searchQuery,
                selection = TextRange(uiState.searchQuery.length)
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmpDark)
            .padding(16.dp),
    ) {
        // Back button
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = AmpGreen,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Search input
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { 
                textFieldValue = it
                viewModel.updateSearchQuery(it.text) 
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Song name or artist...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AmpGreen,
                cursorColor = AmpGreen,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLeadingIconColor = AmpGreen,
                unfocusedLeadingIconColor = Color.Gray,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    viewModel.search()
                    onBack()
                }
            ),
            singleLine = true,
            interactionSource = interactionSource,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Search button (large, driver-friendly)
        Button(
            onClick = {
                viewModel.search()
                onBack()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = uiState.searchQuery.isNotBlank() && !uiState.isSearching,
            colors = ButtonDefaults.buttonColors(
                containerColor = AmpGreen,
                contentColor = Color.Black,
            )
        ) {
            if (uiState.isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.Black,
                )
            } else {
                Text("Play", fontSize = 20.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Hint text
        Text(
            text = "Tip: Add 'lyrics' or 'acoustic' to find specific versions",
            color = Color.Gray,
            fontSize = 14.sp,
        )
        
        // Search history
        if (searchHistory.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Recent Searches",
                color = Color.Gray,
                fontSize = 12.sp,
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn {
                items(searchHistory, key = { it }) { query ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.updateSearchQuery(query)
                                viewModel.search()
                                onBack()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.size(12.dp))
                            Text(
                                text = query,
                                color = Color.White,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            onClick = { viewModel.removeFromHistory(query) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}


