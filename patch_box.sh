#!/bin/bash
cat << 'INNER_EOF' > /tmp/patch_box.txt
<<<<<<< SEARCH
                    leadingContent = {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(40.dp)
                                .androidx.compose.foundation.background(color = circleColor, shape = androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
=======
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .androidx.compose.foundation.background(color = circleColor, shape = androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
>>>>>>> REPLACE
<<<<<<< SEARCH
                    IconButton(onClick = { onIntent(MainIntent.OpenProfilePicker) }) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(32.dp)
                                .androidx.compose.foundation.background(color = circleColor, shape = androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
=======
                    IconButton(onClick = { onIntent(MainIntent.OpenProfilePicker) }) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .androidx.compose.foundation.background(color = circleColor, shape = androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
>>>>>>> REPLACE
INNER_EOF
