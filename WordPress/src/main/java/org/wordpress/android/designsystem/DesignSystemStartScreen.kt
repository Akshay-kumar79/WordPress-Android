package org.wordpress.android.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import org.wordpress.android.R

@Composable
fun DesignSystemStartScreen(
    onNextButtonClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn (
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            dimensionResource(id = R.dimen.reader_follow_sheet_button_margin_top)
        )
    ) {
        item {
            DesignSystemDataSource.startScreenButtonOptions.forEach { item ->
                SelectOptionButton(
                    labelResourceId = item.first,
                    onClick = { onNextButtonClicked(item.second) }
                )
            }
        }
    }
}

@Preview
@Composable
fun StartDesignSystemStartScreenPreview(){
    DesignSystemStartScreen(
        onNextButtonClicked = {},
        modifier = Modifier
            .fillMaxSize()
            .padding(dimensionResource(R.dimen.button_container_shadow_height))
    )
}

