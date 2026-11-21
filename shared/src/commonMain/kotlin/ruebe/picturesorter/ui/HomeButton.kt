package ruebe.picturesorter.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import ruebe.picturesorter.resources.Res
import ruebe.picturesorter.resources.logo

/** Small logo (with lettering) that cancels the current process and returns to the start screen. */
@Composable
fun HomeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.logo),
        contentDescription = "Back to start",
        modifier = modifier
            // The logo is twice as wide as it is high
            .height(48.dp)
            .aspectRatio(2f)
            .clip(RoundedCornerShape(8.dp))
            // Not focusable, so clicking it does not take keyboard focus away from the rating shortcuts
            .focusProperties { canFocus = false }
            .clickable(role = Role.Button, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(2.dp),
    )
}
