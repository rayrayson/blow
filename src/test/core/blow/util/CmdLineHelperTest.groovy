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

package blow.util

import spock.lang.Specification
import org.apache.commons.io.FilenameUtils

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class CmdLineHelperTest extends Specification {

    def "test splitter" () {

        expect:
        args == CmdLineHelper.splitter( cmdline  )

        where:
        cmdline << [
            "-a -b c",
            "-a x    y  ",
            ' a "bb ccc" d ',
            " x 'pp qqq' z "
        ]

        args << [
            ["-a", "-b", "c"],
            ["-a", 'x', 'y'],
            ['a', 'bb ccc', 'd'],
            ['x', 'pp qqq', 'z']

        ]

    }

    def "test getFileTokens" () {

        expect:
        CmdLineHelper.getFileTokens("").name == ""
        CmdLineHelper.getFileTokens("").parentPath == ""
        CmdLineHelper.getFileTokens("").parentFile == new File(".")
        CmdLineHelper.getFileTokens("").absoluteFile == new File(".")

        CmdLineHelper.getFileTokens("hola").name == "hola"
        CmdLineHelper.getFileTokens("hola").parentPath == ""
        CmdLineHelper.getFileTokens("hola").parentFile == new File(".")
        CmdLineHelper.getFileTokens("hola").absoluteFile == new File("./hola")

        CmdLineHelper.getFileTokens("./hola").name == "hola"
        CmdLineHelper.getFileTokens("./hola").parentPath == "./"
        CmdLineHelper.getFileTokens("./hola").parentFile == new File(".")
        CmdLineHelper.getFileTokens("./hola").absoluteFile == new File("./hola")

        CmdLineHelper.getFileTokens("./abc/hola").name == "hola"
        CmdLineHelper.getFileTokens("./abc/hola").parentPath == "./abc/"
        CmdLineHelper.getFileTokens("./abc/hola").parentFile == new File("./abc/")
        CmdLineHelper.getFileTokens("./abc/hola").absoluteFile == new File("./abc/hola")

        CmdLineHelper.getFileTokens("/root/abc/hola").name == "hola"
        CmdLineHelper.getFileTokens("/root/abc/hola").parentPath == "/root/abc/"
        CmdLineHelper.getFileTokens("/root/abc/hola").parentFile == new File("/root/abc/")
        CmdLineHelper.getFileTokens("/root/abc/hola").absoluteFile == new File("/root/abc/hola")


        CmdLineHelper.getFileTokens("~").name == "~"
        CmdLineHelper.getFileTokens("~").parentPath == ""
        CmdLineHelper.getFileTokens("~").parentFile == new File(".")
        CmdLineHelper.getFileTokens("~").absoluteFile == new File( "./~" )

        CmdLineHelper.getFileTokens(".hola").name == ".hola"
        CmdLineHelper.getFileTokens(".hola").parentPath == ""
        CmdLineHelper.getFileTokens(".hola").parentFile == new File(".")
        CmdLineHelper.getFileTokens(".hola").absoluteFile == new File("./.hola")

        CmdLineHelper.getFileTokens("~hola").name == "~hola"
        CmdLineHelper.getFileTokens("~hola").parentPath == ""
        CmdLineHelper.getFileTokens("~hola").parentFile == new File(".")
        CmdLineHelper.getFileTokens("~hola").absoluteFile == new File( "./~hola")

        CmdLineHelper.getFileTokens("~/hola").name == "hola"
        CmdLineHelper.getFileTokens("~/hola").parentPath == "~/"
        CmdLineHelper.getFileTokens("~/hola").parentFile == new File(System.getProperty("user.home"))
        CmdLineHelper.getFileTokens("~/hola").absoluteFile == new File(System.getProperty("user.home"), "hola")

        CmdLineHelper.getFileTokens("~/abc/hola").name == "hola"
        CmdLineHelper.getFileTokens("~/abc/hola").parentPath == "~/abc/"
        CmdLineHelper.getFileTokens("~/abc/hola").parentFile == new File(System.getProperty("user.home"), "abc")
        CmdLineHelper.getFileTokens("~/abc/hola").absoluteFile == new File(System.getProperty("user.home"), "/abc/hola")

        CmdLineHelper.getFileTokens("~/~file").name == "~file"
        CmdLineHelper.getFileTokens("~/~file").parentPath == "~/"
        CmdLineHelper.getFileTokens("~/~file").parentFile == new File(System.getProperty("user.home"))
        CmdLineHelper.getFileTokens("~/~file").absoluteFile == new File(System.getProperty("user.home"), "~file")

        CmdLineHelper.getFileTokens("~/xxx/~file").name == "~file"
        CmdLineHelper.getFileTokens("~/xxx/~file").parentPath == "~/xxx/"
        CmdLineHelper.getFileTokens("~/xxx/~file").parentFile == new File(System.getProperty("user.home"), "xxx")
        CmdLineHelper.getFileTokens("~/xxx/~file").absoluteFile == new File(System.getProperty("user.home"), "xxx/~file")

    }


    def "test getPrefix" () {

        expect:
        FilenameUtils.getName("") == ""
        FilenameUtils.getName(".") == "."
        FilenameUtils.getName("./") == ""
        FilenameUtils.getName("./hola") == "hola"
        FilenameUtils.getName("./hola/ciao") == "ciao"
        FilenameUtils.getName("~") == "~"
        FilenameUtils.getName("~/") == ""
        FilenameUtils.getName("~/abc") == "abc"
        FilenameUtils.getName("~/abc/xxx") == "xxx"

        FilenameUtils.getPath("") == ""
        FilenameUtils.getPath(".") == ""
        FilenameUtils.getPath("./") == "./"
        FilenameUtils.getPath("./hola") == "./"
        FilenameUtils.getPath("./hola/ciao") == "./hola/"
        FilenameUtils.getPath("~") == ""
        FilenameUtils.getPath("~/") == ""
        FilenameUtils.getPath("~/abc") == ""
        FilenameUtils.getPath("~/abc/xxx") == "abc/"

        FilenameUtils.getPrefix("") == ""
        FilenameUtils.getPrefix(".") == ""
        FilenameUtils.getPrefix("./") == ""
        FilenameUtils.getPrefix("./hola") == ""
        FilenameUtils.getPrefix("./hola/ciao") == ""
        FilenameUtils.getPrefix("~") == "~/"
        FilenameUtils.getPrefix("~/") == "~/"
        FilenameUtils.getPrefix("~/abc") == "~/"
        FilenameUtils.getPrefix("~/abc/xxx") == "~/"
    }

}
