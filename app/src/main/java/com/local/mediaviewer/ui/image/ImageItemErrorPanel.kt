package com.local.mediaviewer.ui.image

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.image.ImageItemFailure
import com.local.mediaviewer.image.ImageReaderItem

@Composable
internal fun ImageItemErrorPanel(
    item: ImageReaderItem,
    failure: ImageItemFailure,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp)
            .background(Color.DarkGray)
            .padding(16.dp),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center,
    ) {
        Text(
            text = item.name,
            color = Color.White,
        )
        Text(
            text = failure.message,
            color = Color.White,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.testTag(
                "retry_image:${item.logicalUrl}",
            ),
        ) {
            Text("重试此图")
        }
    }
}
