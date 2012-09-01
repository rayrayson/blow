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

import spock.lang.Specification

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class S3CommandTest extends Specification {

    def "strToFile" () {
        expect:
        S3commands.strToFile(null) == new File('.')
        S3commands.strToFile('.') == new File('.')
        S3commands.strToFile("~") == new File(System.properties['user.home'])
        S3commands.strToFile("~/") == new File(System.properties['user.home'],'/')
        S3commands.strToFile("~/abc") == new File(System.properties['user.home'],'abc')
        S3commands.strToFile("~abc") == new File('~abc')
    }

    def "test location " () {
        when:
        def loc1 = S3Path.split('')
        def loc2 = S3Path.split('bucket/')
        def loc3 = S3Path.split('bucket/path')
        def loc4 = S3Path.split('bucket/user/path')
        def loc5 = S3Path.split('bucket/user/path/')
        def loc6 = S3Path.split('/bucket/user/path/')
        def loc7 = S3Path.split('/')
        def loc8 = S3Path.split('/hola')


        then:
        loc1.container == ''
        loc1.directory == ''

        loc2.container == 'bucket'
        loc2.directory == ''

        loc3.container == 'bucket'
        loc3.directory == 'path'

        loc4.container == 'bucket'
        loc4.directory == 'user/path'

        loc5.container == 'bucket'
        loc5.directory == 'user/path'

        loc6.container == 'bucket'
        loc6.directory == 'user/path'

        loc7.container == ''
        loc7.directory == ''

        loc8.container == 'hola'
        loc8.directory == ''

    }


    def "test formart " () {

        when:
        def loc1 = new S3Path()
        def loc2 = new S3Path(container: 'root')
        def loc3 = new S3Path(container: 'root', directory: 'dir1')

        then:
        loc1.format() == ''
        loc1.format('hola') == 'hola'

        loc2.format() == 'root'
        loc2.format('file') == 'root/file'

        loc3.format() == 'root/dir1'
        loc3.format('file') == 'root/dir1/file'
    }
}
