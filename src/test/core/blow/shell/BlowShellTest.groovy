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

package blow.shell

import spock.lang.Specification

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BlowShellTest extends Specification {

    def "test init method" () {

        setup:
        def shell = new BlowShell()

        expect:
        shell.init(args)
        shell.mainEntry == mainEntry
        shell.mainCommand == mainCommand


        where:
        args << [
                ['basic'],
                ['cluster', 'listnodes'],
                ['cluster', 'nodeinfo', 'xxx', 'yyy'],
                ['help', 'hola']
        ]
        mainEntry << [
                'basic',
                'cluster',
                'cluster',
                'help'
                ]
        mainCommand << [
                null,
                [name:'listnodes', args:[]],
                [name:'nodeinfo', args:['xxx','yyy']],
                [name: 'hola', args: []]
                ]

    }


    def "test addAction method"() {

        setup:
        def shell = new BlowShell()
        def action = new AbstractShellCommand() {
            public String getName() { "hola" }
            public void invoke() {}
            public String getSynopsis() {}
        };

        when:
        shell.addCommand( action )

        then:
        shell.availableCommands.containsKey("hola")
        shell.availableCommands.containsValue(action)


    }


    def "test CliBuilder " () {


        setup:
        CliBuilder cli = new CliBuilder()
        cli.usage = "blow [options] cluster-config"
        cli._( longOpt: "debug", "Show debug information")
        cli.h( longOpt: "help", "Show this help")

        expect:
        def options = cli.parse(cmdline)
        options.debug == debug
        options.help == help
        options.arguments() == args

        where:
        cmdline <<  [ ["--debug","x"], ["-h","y"], ["--help","w", "z"], ["--debug", "hola", "-h"] ]
        debug << [ true, false, false, true ]
        help << [ false, true, true, false ]
        args << [ ["x"], ["y"], ["w", "z"], [ "hola", "-h"] ]


    }


}
