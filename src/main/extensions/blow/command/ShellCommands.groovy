/*
 * Copyright (c) 2012, the authors.
 *
 *   This file is part of Blow.
 *
 *   Blow is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Blow is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Blow.  If not, see <http://www.gnu.org/licenses/>.
 */

package blow.command

import blow.Project
import blow.exception.ShellExit
import blow.shell.BlowShell
import blow.shell.Cmd
import blow.shell.Completion

/**
 * Define the Blow shell basic commands
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ShellCommands {

    BlowShell shell

    @Cmd(summary='Print this help')
    @Completion( { cmdline -> helpOptions(cmdline) } )
    def void help(String cmd)
    {

        if( !cmd ) {
            shell.printUsage()
        }
        else if( shell.availableCommands[cmd] ) {
            def help = shell.availableCommands[cmd].getHelp()?.trim()
            println help ?: "(no help available)"
        }
        else {
            println "Unknown command: '${cmd}'"
        }

    }

    @Override
    List<String> helpOptions(String cmdline) {
        def result = []
        shell.availableCommands.keySet().each() { if(!cmdline || it.startsWith(cmdline)) result.add(it) }
        result.sort()
    }

    /**
     * Defines the 'use' command to switch between different configuration
     */
    @Cmd(summary="Switch to a different cluster configuration")
    @Completion({ String cmdline -> useOptions(cmdline) })
    def void use(String clusterName)
    {
        println "Switching > ${clusterName}"
        shell.useCluster(clusterName)
    }

    List<String> useOptions(String cmdline) {
        shell.listClusters().findAll { it?.startsWith(cmdline) }
    }

    /**
     * Print the Blow version
     */
    @Cmd(summary="Print the program version and build info")
    def void version () {
        println "Version: ${Project.version} - Build timestap: ${Project.timestamp.format('dd/MM/yyyy HH.mm')}"
    }

    /**
     * Terminate the Blow shell session
     */
    @Cmd(summary="Quit the current shell session")
    def void exit() {
        throw new ShellExit()
    }


}
