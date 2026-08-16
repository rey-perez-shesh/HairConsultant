package com.hairconsultant.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hairconsultant.app.domain.model.Consultation
import com.hairconsultant.app.ui.components.StarRatingBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(viewModel: ProfileViewModel, onLoggedOut: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(20.dp))
                NavigationDrawerItem(
                    label = { Text("Clear history") },
                    icon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                    selected = false,
                    onClick = { viewModel.clearHistory(); scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Delete saved favorites") },
                    icon = { Icon(Icons.Filled.FavoriteBorder, contentDescription = null) },
                    selected = false,
                    onClick = { viewModel.clearFavorites(); scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    label = { Text("Log out") },
                    icon = { Icon(Icons.Filled.Logout, contentDescription = null) },
                    selected = false,
                    onClick = {
                        viewModel.logout()
                        scope.launch { drawerState.close() }
                        onLoggedOut()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Profile") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Settings")
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item { ProfileHeader(email = uiState.user?.email.orEmpty(), username = uiState.user?.username.orEmpty()) }

                item {
                    TabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                        Tab(
                            selected = uiState.selectedTab == ProfileTab.HISTORY,
                            onClick = { viewModel.onTabSelected(ProfileTab.HISTORY) },
                            text = { Text("History") },
                            icon = { Icon(Icons.Filled.History, contentDescription = null) }
                        )
                        Tab(
                            selected = uiState.selectedTab == ProfileTab.FAVORITES,
                            onClick = { viewModel.onTabSelected(ProfileTab.FAVORITES) },
                            text = { Text("Favorites") },
                            icon = { Icon(Icons.Filled.Favorite, contentDescription = null) }
                        )
                    }
                }

                val list = if (uiState.selectedTab == ProfileTab.HISTORY) uiState.history else uiState.favorites
                if (list.isEmpty()) {
                    item {
                        Text(
                            "Nothing here yet — try a face scan or upload a photo.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                } else {
                    items(list, key = { it.id }) { consultation ->
                        ConsultationRow(consultation = consultation, onToggleFavorite = { viewModel.toggleFavorite(consultation) })
                    }
                }

                item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }

                item {
                    FeedbackSection(
                        rating = uiState.rating,
                        comment = uiState.feedbackComment,
                        submitted = uiState.feedbackSubmitted,
                        onRatingChanged = viewModel::onRatingChanged,
                        onCommentChanged = viewModel::onFeedbackCommentChanged,
                        onSubmit = viewModel::submitFeedback
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(email: String, username: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(12.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(username.ifBlank { "Your account" }, style = MaterialTheme.typography.titleMedium)
            Text(email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConsultationRow(consultation: Consultation, onToggleFavorite: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = consultation.selectedHaircut?.imageUrl ?: consultation.resultImageUrl ?: consultation.sourceImageUrl,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    consultation.selectedHaircut?.name ?: "${consultation.scanResult.faceShape.displayName} scan",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "${consultation.scanResult.hairLength.displayName} • ${consultation.scanResult.hairTexture.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (consultation.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Toggle favorite",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun FeedbackSection(
    rating: Int,
    comment: String,
    submitted: Boolean,
    onRatingChanged: (Int) -> Unit,
    onCommentChanged: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text("How satisfied are you?", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        StarRatingBar(rating = rating, onRatingChanged = onRatingChanged)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Tell us about your experience", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = comment,
            onValueChange = onCommentChanged,
            modifier = Modifier.fillMaxWidth().height(120.dp),
            placeholder = { Text("What worked well? What could be better?") }
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onSubmit, enabled = rating > 0) { Text("Submit Feedback") }
            if (submitted) {
                Text("Thanks for your feedback!", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
