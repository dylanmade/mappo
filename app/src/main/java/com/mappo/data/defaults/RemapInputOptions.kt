package com.mappo.data.defaults

import com.mappo.data.model.OutputNames
import com.mappo.data.model.RemapTarget
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

data class InputOption(val label: String, val target: RemapTarget)

/** The command picker's option lists — names and order both come from [OutputNames]. */
object RemapInputOptions {

    val gamepadOptions: ImmutableList<InputOption> =
        OutputNames.gamepad.map { (code, name) -> InputOption(name, RemapTarget.Gamepad(code)) }.toImmutableList()

    val keyboardOptions: ImmutableList<InputOption> =
        OutputNames.keyboard.map { (code, name) -> InputOption(name, RemapTarget.Keyboard(code)) }.toImmutableList()

    val mouseOptions: ImmutableList<InputOption> =
        OutputNames.mouse.map { (code, name) -> InputOption(name, RemapTarget.Mouse(code)) }.toImmutableList()
}
