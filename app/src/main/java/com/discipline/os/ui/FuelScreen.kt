package com.discipline.os.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.discipline.os.data.FuelEntry

@Composable
fun FuelScreen(
    fuelList: List<FuelEntry>,
    onAddFuel: (String, String, String) -> Unit,
    onDeleteFuel: (FuelEntry) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBg)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = (if (navBarBottom > 48.dp) navBarBottom else 48.dp) + 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Motivation Banner (TripGlide Style)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 5.dp, shape = RoundedCornerShape(26.dp), spotColor = Color(0x18000000))
                    .border(1.dp, BorderSubtle, RoundedCornerShape(26.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(AccentFlameSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = AccentFlame,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "PROVE THEM WRONG",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    letterSpacing = 0.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Raw Fuel & Motivation",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Sleek Add Fuel Pill Button - Never wraps or squishes
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = AccentFlameSoft,
                            border = BorderStroke(1.dp, AccentFlame.copy(alpha = 0.4f)),
                            modifier = Modifier.clickable { showDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add",
                                    tint = AccentFlame,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Add Fuel",
                                    color = AccentFlame,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Remember everyone who doubted, mocked, or embarrassed you. Convert every insult into relentless compounding execution until your results speak for themselves.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // 2. Section Header
        item {
            Text(
                text = "The Chip On Your Shoulder (${fuelList.size})",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )
        }

        // 3. Fuel Cards List
        if (fuelList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No fuel logged yet. Add your doubters to fuel your fire.",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            items(fuelList, key = { it.id }) { item ->
                ModernFuelCard(
                    fuel = item,
                    onDelete = { onDeleteFuel(item) }
                )
            }
        }
    }

    if (showDialog) {
        ModernAddFuelDialog(
            onDismiss = { showDialog = false },
            onSave = { person, vow, cat ->
                onAddFuel(person, vow, cat)
                showDialog = false
            }
        )
    }
}

@Composable
fun ModernFuelCard(
    fuel: FuelEntry,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(22.dp), spotColor = Color(0x12000000))
            .border(1.dp, BorderSubtle, RoundedCornerShape(22.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Doubter Pill Tag - Flexible with maxLines & ellipsis protection
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentFlameSoft)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "DOUBTER: ${fuel.personOrIncident}",
                        color = AccentFlame,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Delete Button - Fixed size, guaranteed no squishing or overlap
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CanvasBg)
                        .border(1.dp, BorderSubtle, CircleShape)
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Defiance Vow Quote
            Text(
                text = "\"${fuel.defianceVow}\"",
                color = TextPrimary,
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ModernAddFuelDialog(
    onDismiss: () -> Unit,
    onSave: (person: String, vow: String, cat: String) -> Unit
) {
    var person by remember { mutableStateOf("") }
    var vow by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Doubter") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardWhite,
        titleContentColor = TextPrimary,
        shape = RoundedCornerShape(26.dp),
        title = {
            Text(text = "Add Fuel to the Fire", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = person,
                    onValueChange = { person = it },
                    label = { Text("Doubter / Incident / Teacher") },
                    placeholder = { Text("e.g. Teacher who embarrassed me in class") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryActionBg,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = TextPrimary,
                        unfocusedLabelColor = TextSecondary,
                        cursorColor = PrimaryActionBg
                    )
                )

                OutlinedTextField(
                    value = vow,
                    onValueChange = { vow = it },
                    label = { Text("What they said & Your Vow") },
                    placeholder = { Text("e.g. Said I'd fail. I will be better than him.") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryActionBg,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = TextPrimary,
                        unfocusedLabelColor = TextSecondary,
                        cursorColor = PrimaryActionBg
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (person.isNotBlank() && vow.isNotBlank()) {
                        onSave(person, vow, category)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryActionBg),
                shape = CircleShape
            ) {
                Text("Add Fuel", color = PrimaryActionFg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
