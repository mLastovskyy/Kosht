package by.mlastovsky.kosht.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import by.mlastovsky.kosht.R

class PictureChoice(private val storedPath: String?) {
    var picked by mutableStateOf<Uri?>(null)
        private set
    var cleared by mutableStateOf(false)
        private set

    val path: String? get() = storedPath.takeIf { !cleared }

    val hasPicture: Boolean get() = picked != null || path != null

    fun pick(uri: Uri) {
        picked = uri
        cleared = false
    }

    fun clear() {
        picked = null
        cleared = true
    }
}

@Composable
fun rememberPictureChoice(storedPath: String?): PictureChoice =
    remember(storedPath) { PictureChoice(storedPath) }

@Composable
fun PicturePickerRow(choice: PictureChoice, modifier: Modifier = Modifier) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) choice.pick(uri) }
    val fromFile = rememberBitmapFromPath(choice.path, maxDimension = PREVIEW_PIXELS)
    val fromPick = rememberBitmapFromUri(choice.picked, maxDimension = PREVIEW_PIXELS)
    val picture = fromPick ?: fromFile

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (picture != null) {
                Image(
                    bitmap = picture,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Rounded.AddAPhoto,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = stringResource(
                if (choice.hasPicture) R.string.category_picture_set else R.string.category_picture
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (choice.hasPicture) {
            TextButton(onClick = { choice.clear() }) {
                Text(stringResource(R.string.category_picture_remove))
            }
        }
    }
}

private const val PREVIEW_PIXELS = 256
