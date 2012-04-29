/*
 * Copyright (c) 2012. Paolo Di Tommaso.
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

package blow

import blow.shell.Cmd
import blow.shell.BlowShell

/**
 * Created by IntelliJ IDEA.
 * User: yo
 * Date: 4/28/12
 * Time: 12:26 AM
 * To change this template use File | Settings | File Templates.
 */
class TestShellMethods {

    def BlowSession session

    def BlowShell shell


    @Cmd
    def command1 () {}

    def command2 () {}

    @Cmd("cmd_3")
    def command3 () { def args }

}
