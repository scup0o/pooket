package com.project.pooket.core.navigation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigationManager @Inject constructor() {
    sealed class Command {
        data class Navigate(val route: String) : Command()
        data object GoBack : Command()
        data object OpenDrawer : Command()
    }

    private val _commands = Channel<Command>(Channel.BUFFERED)
    val commands = _commands.receiveAsFlow()

    fun navigate(route: String) {
        _commands.trySend(Command.Navigate(route))
    }

    fun goBack() {
        _commands.trySend(Command.GoBack)
    }

    fun openDrawer() {
        _commands.trySend(Command.OpenDrawer)
    }
}