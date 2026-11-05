package app.cardfilter.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

private sealed interface ImageState {
    data object Loading : ImageState

    data object Failed : ImageState

    class Loaded(val bitmap: ImageBitmap) : ImageState
}

/**
 * A card's stored image. Loads the bytes with [load] and decodes them off the main thread.
 *
 * While loading it shows a neutral tile of the size given by [modifier], so a list row does not
 * jump when the image arrives. When there are no bytes, or they are not a decodable image, it
 * draws nothing at all: a missing image is a normal state, never an error.
 *
 * [imageKey] identifies the image: it is reloaded only when the key changes, not when the caller
 * passes a new [load] lambda on recomposition.
 */
@Composable
fun CardImage(
    imageKey: Any,
    load: suspend () -> ByteArray?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val currentLoad by rememberUpdatedState(load)
    val state by produceState<ImageState>(ImageState.Loading, imageKey) {
        value = withContext(Dispatchers.Default) { decode(currentLoad()) }
    }
    when (val current = state) {
        ImageState.Loading -> Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
        ImageState.Failed -> Unit
        is ImageState.Loaded -> Image(
            bitmap = current.bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}

// Android throws NullPointerException and iOS IllegalArgumentException for bytes that are not an
// image (seen in the decode spike), so any Exception means "no image".
private fun decode(bytes: ByteArray?): ImageState {
    if (bytes == null) return ImageState.Failed
    return try {
        ImageState.Loaded(bytes.decodeToImageBitmap())
    } catch (_: Exception) {
        ImageState.Failed
    }
}
