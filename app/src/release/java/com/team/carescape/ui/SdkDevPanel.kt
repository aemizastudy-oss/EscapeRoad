package com.team.carescape.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Release includes no mode selector or simulated observations. */
@Composable
fun SdkDevPanel(vm: GameViewModel, modifier: Modifier = Modifier) = Unit

@Composable
fun MockArrivalAction(vm: GameViewModel, modifier: Modifier = Modifier) = Unit

@Composable
fun MockTourTimeAction(vm: GameViewModel, modifier: Modifier = Modifier) = Unit
