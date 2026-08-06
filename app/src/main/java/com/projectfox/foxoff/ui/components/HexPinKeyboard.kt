package com.projectfox.foxoff.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun HexPinKeyboard(
    pin: String,
    onPinChanged: (String) -> Unit
) {

    val rows = listOf(
        listOf("A","B","C","D"),
        listOf("E","F","0","1"),
        listOf("2","3","4","5"),
        listOf("6","7","8","9")
    )

    Column {

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {

            repeat(6) { index ->

                OutlinedTextField(
                    value = pin.getOrNull(index)?.toString() ?: "",
                    onValueChange = {},
                    enabled = false,
                    modifier = Modifier
                        .size(56.dp)
                        .padding(2.dp),
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                        textAlign = TextAlign.Center
                    ),
                    colors = TextFieldDefaults.colors()
                )
            }
        }

        rows.forEach { row ->

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {

                row.forEach { key ->

                    Button(
                        modifier = Modifier
                            .padding(4.dp)
                            .weight(1f),

                        shape = RoundedCornerShape(12.dp),

                        onClick = {

                            if (pin.length < 6) {
                                onPinChanged(pin + key)
                            }

                        }
                    ) {
                        Text(key)
                    }
                }
            }
        }

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),

            onClick = {

                if (pin.isNotEmpty()) {
                    onPinChanged(pin.dropLast(1))
                }

            }
        ) {

            Icon(
                Icons.Default.Backspace,
                contentDescription = null
            )

            Text(" Effacer")

        }
    }
}