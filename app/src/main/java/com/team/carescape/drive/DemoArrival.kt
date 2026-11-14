package com.team.carescape.drive

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.team.carescape.game.StoryPhase
import com.team.carescape.ui.GameViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Demo (MOCK) trips only: when the demo car reaches the place and parks, the app — once it is on screen,
 * however it got there — does by hand what the presenter would do: confirm P (the car really is in P),
 * "도착한 것으로 하기 (시연)", then "관광 시작". The game moves through its own existing steps and shows its
 * existing "<place>을 둘러보는 중" screen, the page that follows the 출발하기 page after arriving.
 * LIVE trips are untouched: the game confirms arrival from the real position.
 */
internal object DemoArrival {
    /** Chapter whose demo drive has arrived and parked; set by [DriveService] in the same process. */
    val pending = MutableStateFlow<Int?>(null)

    fun watch(activity: ComponentActivity, vm: GameViewModel) {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                pending.filterNotNull().collect { chapter -> confirm(vm, chapter) }
            }
        }
    }

    private suspend fun confirm(vm: GameViewModel, chapter: Int) {
        // The saved trip may still be loading right after the app comes back.
        repeat(40) {
            if (vm.isMock && vm.story?.chapterIndex == chapter) {
                vm.simSetParked(true)
                if (vm.parked) {
                    vm.demoArrive()
                    startTour(vm)
                    Log.i("DemoArrival", "demo arrival confirmed for chapter $chapter, phase ${vm.story?.phase}")
                    pending.value = null
                    return
                }
            }
            delay(250)
        }
        pending.value = null
    }

    /** The arrival screen's "관광 시작" (GameViewModel.leaveCar) once the arrival has been saved. */
    private suspend fun startTour(vm: GameViewModel) {
        repeat(40) {
            when {
                vm.story?.phase == StoryPhase.EXPLORING -> return
                vm.story?.phase == StoryPhase.ARRIVED && !vm.saving -> vm.leaveCar()
            }
            delay(250)
        }
    }
}
