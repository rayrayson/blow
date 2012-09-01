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

package blow.util

import com.google.common.collect.ArrayListMultimap
import com.thoughtworks.xstream.XStream
import spock.lang.Shared
import spock.lang.Specification

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ArrayListMultimapConverterTest extends Specification {

    @Shared
    def XStream xstream

    def setupSpec() {
        xstream = new XStream()
        xstream.registerConverter( new ArrayListMultimapConverter(xstream.getMapper()) )
        xstream.alias( "arraylist-multimap", ArrayListMultimap.class )
    }


    def "test marshall" () {
        when:
        def EXPECTED =
        """\
        <arraylist-multimap>
          <entry>
            <null/>
            <values>
              <null/>
            </values>
          </entry>
          <entry>
            <string>gamma</string>
            <values>
              <null/>
            </values>
          </entry>
          <entry>
            <string>alpha</string>
            <values>
              <string>1</string>
              <string>2</string>
              <string>3</string>
            </values>
          </entry>
          <entry>
            <string>beta</string>
            <values>
              <string>4</string>
            </values>
          </entry>
        </arraylist-multimap>"""
        .stripIndent()

        def map = new ArrayListMultimap<String,String>()
        map.put('alpha', '1')
        map.put('alpha', '2')
        map.put('alpha', '3')
        map.put('beta', '4')
        map.put('gamma', null)
        map.put(null,null)

        then:
        xstream.toXML(map) == EXPECTED
    }

    def "test unmarshall"() {
        when:
        def XML =
            """\
            <arraylist-multimap>
            <entry>
                <string>alpha</string>
                <values><string>1</string><string>2</string><string>3</string></values>
            </entry>
            <entry>
                <string>beta</string>
                <values><string>4</string></values>
            </entry>
            <entry>
                <string>gamma</string>
                <values> <null/> </values>
            </entry>
            <entry>
                <null/>
                <values><null/></values>
            </entry>
            </arraylist-multimap>
            """
        ArrayListMultimap<String,String> map = xstream.fromXML(XML)

        then:
        map.containsKey('alpha')
        map.get('alpha').get(0) == '1'
        map.get('alpha').get(1) == '2'
        map.get('alpha').get(2) == '3'
        map.get('alpha').size() == 3

        map.containsKey('beta')
        map.get('beta').get(0) == '4'
        map.get('beta').size() == 1

        map.containsEntry('gamma',null)
        map.get('gamma').size()==1

        map.containsEntry(null,null)


        !map.containsKey('delta')

    }
}
