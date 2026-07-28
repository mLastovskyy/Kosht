package by.mlastovsky.kosht.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.ui.AppViewModelProvider
import by.mlastovsky.kosht.ui.components.Avatar
import by.mlastovsky.kosht.ui.components.EMOJI_AVATAR_PREFIX
import by.mlastovsky.kosht.ui.components.PRESET_AVATARS
import by.mlastovsky.kosht.ui.components.TextInput

@Composable
fun ProfileDialog(
    onDismiss: () -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val current = profile ?: return
    val defaultName = stringResource(R.string.profile_default_name)

    var nickname by remember(current.nickname) { mutableStateOf(current.nickname) }
    var confirmRemovePhoto by remember { mutableStateOf(false) }
    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.setPhoto(uri) }

    if (confirmRemovePhoto) {
        AlertDialog(
            onDismissRequest = { confirmRemovePhoto = false },
            title = { Text(stringResource(R.string.photo_remove)) },
            text = { Text(stringResource(R.string.photo_remove_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removePhoto()
                        confirmRemovePhoto = false
                    }
                ) {
                    Text(
                        stringResource(R.string.editor_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemovePhoto = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_profile)) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Avatar(
                    photoPath = current.photoPath,
                    fallbackText = nickname.ifBlank { defaultName },
                    size = 72.dp,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            photoLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        onLongClick = {
                            if (current.photoPath != null) confirmRemovePhoto = true
                        }
                    )
                )
                Text(
                    text = stringResource(R.string.profile_photo_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PRESET_AVATARS.size) { index ->
                        val emoji = PRESET_AVATARS[index]
                        Avatar(
                            photoPath = EMOJI_AVATAR_PREFIX + emoji,
                            fallbackText = emoji,
                            size = 44.dp,
                            modifier = Modifier.clickable { viewModel.setEmoji(emoji) }
                        )
                    }
                }
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it.take(24) },
                    label = { Text(stringResource(R.string.profile_nickname)) },
                    supportingText = { Text(stringResource(R.string.profile_nickname_hint)) },
                    singleLine = true,
                    keyboardOptions = TextInput.Name,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.save(nickname)
                    onDismiss()
                }
            ) { Text(stringResource(R.string.editor_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
