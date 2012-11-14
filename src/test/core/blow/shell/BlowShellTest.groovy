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

package blow.shell

import spock.lang.Specification

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BlowShellTest extends Specification {



    def "test addAction method"() {

        setup:
        def shell = new BlowShell()
        def action = new AbstractShellCommand() {
            public String getName() { "hola" }
            public void invoke() {}
            public String getSummary() {}
        };

        when:
        shell.addCommand( action )

        then:
        shell.availableCommands.containsKey("hola")
        shell.availableCommands.containsValue(action)


    }

    def "test addOrReplaceAccessProperties" () {

        when:
        def text = ''
        def result = BlowShell.addOrReplaceAccessProperties(text,'aaa', 'bbb', 'ccc')
        def expected =
            """\
            accessKey 'aaa'
            secretKey 'bbb'
            accountId 'ccc'
            """
            .stripIndent()

        then:
        result == expected

    }

    def "test addOrReplaceAccessProperties (2)" () {

        when:
        def text =
            """\
            accessKey 'j53jk5'
            secretKey 'k42j4k2j'
            accountId '9ds90ds0'
            """
                    .stripIndent()
        def result = BlowShell.addOrReplaceAccessProperties(text,'abc', 'efg', 'xyz')
        def expected =
            """\
            accessKey 'abc'
            secretKey 'efg'
            accountId 'xyz'
            """
                    .stripIndent()

        then:
        result == expected

    }



    def "test addOrReplaceAccessProperties (3)" () {

        when:
        def text =
            """\
            accessKey 'j53jk5'
            secretKey 'k42j4k2j'
            accountId '9ds90ds0'
            """
                    .stripIndent()
        def result = BlowShell.addOrReplaceAccessProperties(text,'abc', 'efg', '')
        def expected =
            """\
            accessKey 'abc'
            secretKey 'efg'

            """
                    .stripIndent()

        then:
        result == expected

    }

    def "test addOrReplaceAccessProperties (4)" () {

        when:
        def text =
            """\
            secretKey 'k42j4k2j'
            """
                    .stripIndent()
        def result = BlowShell.addOrReplaceAccessProperties(text, 'abc', 'efg', '8899')
        def expected =
            """\
            secretKey 'efg'
            accessKey 'abc'
            accountId '8899'
            """
                    .stripIndent()

        then:
        result == expected

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


    def "test findMatches" (){

        expect:
        BlowShell.findBestMatchesFor('hola', ['alpha','beta','gamma']) == []
        BlowShell.findBestMatchesFor('halo', ['goodbye','hola','ciao']) == ['hola']
    }


}
