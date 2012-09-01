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

import com.google.common.collect.HashBiMap
import com.thoughtworks.xstream.XStream
import com.thoughtworks.xstream.io.xml.StaxDriver
import spock.lang.Shared
import spock.lang.Specification

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class HashBiMapConverterTest extends Specification {

    @Shared
    def XStream xstream

    def setupSpec() {
        xstream = new XStream(new StaxDriver())
        xstream.registerConverter( new HashBiMapConverter(xstream.getMapper()) )
        xstream.alias( "hash-bimap", HashBiMap.class )
    }

    def "test marshal" () {

        when:
        HashBiMap<String,String> map = HashBiMap.create()

        map.put("uno", "1")
        map.put("due", "2" )


        then:
        xstream.toXML(map) == "<?xml version=\"1.0\" ?><hash-bimap><entry><string>due</string><string>2</string></entry><entry><string>uno</string><string>1</string></entry></hash-bimap>"

    }

    def "test unmarshall" () {
        when:
        def XML = "<?xml version=\"1.0\" ?><hash-bimap><entry><string>due</string><string>2</string></entry><entry><string>uno</string><string>1</string></entry></hash-bimap>"
        HashBiMap<String,String> map = xstream.fromXML(XML)

        then:
        map.containsKey('uno')
        map.containsKey('due')
        !map.containsKey('tre')

        map.get('uno') == '1'
        map.get('due') == '2'
        map.get('tre') == null
    }
}
