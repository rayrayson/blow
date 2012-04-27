/*
 * Copyright (c) 2012. Paolo Di Tommaso
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

import spock.lang.Specification
import blow.util.CmdLine

/**
 * @author Paolo Di Tommaso
 * Date: 4/11/12
 * Time: 1:06 PM
 */
class SshCommandTest extends Specification{

    def "test parse" () {
        setup:
        def ssh = new SshCommand();

        expect:
        ssh.parse( CmdLine.splitter(cmdline) )
        ssh.targetHost == target
        ssh.targetCommand == targetCommand

        where:
        cmdline << [ "hostname", "host something more", "node.com     blanks", "  node   args  " ]
        target << [ "hostname",   "host", "node.com", "node" ]
        targetCommand << [ null, "something more", "blanks", "args" ]
    }

    def "test make regexp" () {

        expect:
        "1\\.2\\.3\\.4" == SshCommand.makeRegexp("1.2.3.4")
        "alpha" == SshCommand.makeRegexp("alpha")
        "alpha.*" == SshCommand.makeRegexp("alpha*")
    }
}
