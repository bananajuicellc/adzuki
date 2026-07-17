#!/bin/bash
cat << 'INNER_EOF' > /tmp/patch_modifiers.txt
<<<<<<< SEARCH
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .androidx.compose.foundation.background(color = circleColor, shape = androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
=======
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(color = circleColor, shape = androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
>>>>>>> REPLACE
<<<<<<< SEARCH
                    IconButton(onClick = { onIntent(MainIntent.OpenProfilePicker) }) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .androidx.compose.foundation.background(color = circleColor, shape = androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
=======
                    IconButton(onClick = { onIntent(MainIntent.OpenProfilePicker) }) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(color = circleColor, shape = androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
>>>>>>> REPLACE
INNER_EOF
