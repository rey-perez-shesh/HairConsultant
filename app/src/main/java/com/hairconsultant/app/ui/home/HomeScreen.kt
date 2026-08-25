package com.hairconsultant.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.Gender
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.ui.chatbot.ChatBotSheet
import com.hairconsultant.app.ui.components.ChatFab
import com.hairconsultant.app.ui.components.ClusterRow
import com.hairconsultant.app.ui.components.FilterDropdown
import com.hairconsultant.app.ui.components.HaircutDetailDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val chatState by viewModel.chatState.collectAsState()
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoading && uiState.clusters.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 20.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "Find your next haircut",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Text(
                        text = "Browse by length and texture, or ask the AI stylist",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
                item {
                    CatalogFilterRow(
                        uiState = uiState,
                        onGenderSelected = viewModel::setGenderFilter,
                        onFaceShapeSelected = viewModel::setFaceShapeFilter,
                        onHairLengthSelected = viewModel::setHairLengthFilter,
                        onHairTextureSelected = viewModel::setHairTextureFilter,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
                items(uiState.clusters, key = { it.length.name + it.texture.name }) { cluster ->
                    ClusterRow(
                        cluster = cluster,
                        onHaircutClick = viewModel::onHaircutSelected
                    )
                }
            }
        }

        ChatFab(
            onClick = { viewModel.chatBot.setOpen(true) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        )
    }

    if (chatState.isOpen) {
        ChatBotSheet(
            state = chatState,
            sheetState = sheetState,
            onDismiss = { viewModel.chatBot.setOpen(false) },
            onInputChange = viewModel.chatBot::updateInput,
            onSend = { scope.launch { viewModel.chatBot.sendCurrentInput() } },
            onHaircutSelected = viewModel::onHaircutSelected
        )
    }

    uiState.selectedHaircut?.let { haircut ->
        HaircutDetailDialog(
            haircut = haircut,
            onDismiss = viewModel::dismissHaircutDetail,
            onTryOn = { viewModel.onTryOnClicked(haircut) }
        )
    }

    uiState.cameraTryOnHaircut?.let { haircut ->
        Dialog(
            onDismissRequest = viewModel::closeCameraTryOn,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            CameraTryOnScreen(haircut = haircut, onClose = viewModel::closeCameraTryOn)
        }
    }
}

/**
 * Home defaults to the signed-in user's gender (masculine/unisex or feminine/unisex styles); these
 * four dropdowns let them look past that default at any other gender, face shape, hair type, or
 * hair length in the catalog.
 */
@Composable
private fun CatalogFilterRow(
    uiState: HomeUiState,
    onGenderSelected: (Gender?) -> Unit,
    onFaceShapeSelected: (FaceShape?) -> Unit,
    onHairLengthSelected: (HairLength?) -> Unit,
    onHairTextureSelected: (HairTexture?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterDropdown(
                label = "Gender",
                selected = uiState.genderFilter,
                options = Gender.entries,
                optionLabel = { it.displayName },
                onSelected = onGenderSelected,
                modifier = Modifier.weight(1f)
            )
            FilterDropdown(
                label = "Face shape",
                selected = uiState.faceShapeFilter,
                options = FaceShape.entries,
                optionLabel = { it.displayName },
                onSelected = onFaceShapeSelected,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterDropdown(
                label = "Hair type",
                selected = uiState.hairTextureFilter,
                options = HairTexture.entries,
                optionLabel = { it.displayName },
                onSelected = onHairTextureSelected,
                modifier = Modifier.weight(1f)
            )
            FilterDropdown(
                label = "Hair length",
                selected = uiState.hairLengthFilter,
                options = HairLength.entries,
                optionLabel = { it.displayName },
                onSelected = onHairLengthSelected,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
