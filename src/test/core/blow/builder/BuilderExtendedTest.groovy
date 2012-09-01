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

package blow.builder

import spock.lang.Specification

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BuilderExtendedTest extends Specification {

    def "test basic"() {

        when:
        def builder = new MyBuilder()
        def graph = builder .conf { }

        then:
        graph instanceof Expando
        graph.name == 'conf'

    }

    def "test elems"() {

        when:
        def builder = new MyBuilder()
        def conf = builder.conf {

            elem0 { }

            elem1 (1)

            elem2 ( x:1, y:2, z: 3)

            elem3 'a','b','c'

            elem4 (1) {
                attr1 1
                attr2 2
            }

            elem5 (a:11, b:22) {
                attr1 1
                attr2 2
            }

            elem6 (99, a:11, b:22) {
                attr1 1
                attr2 2
            }

            elem7 (1,2,3,a:11, b:22) {
                attr1 1
                attr2 2
            }

        }


        then:
        conf instanceof Expando
        conf.name == 'conf'

        conf.children[0].name == 'elem0'

        conf.children[1].name == 'elem1'
        conf.children[1].value == 1

        conf.children[2].name == 'elem2'
        conf.children[2].attributes.x == 1
        conf.children[2].attributes.y == 2
        conf.children[2].attributes.z == 3

        conf.children[3].name == 'elem3'
        conf.children[3].value == ['a','b','c']

        conf.children[4].name == 'elem4'
        conf.children[4].value == 1
        conf.children[4].children[0].name == 'attr1'
        conf.children[4].children[0].value == 1
        conf.children[4].children[1].name == 'attr2'
        conf.children[4].children[1].value == 2


        conf.children[5].name == 'elem5'
        conf.children[5].attributes.a == 11
        conf.children[5].attributes.b == 22
        conf.children[5].children[0].name == 'attr1'
        conf.children[5].children[0].value == 1
        conf.children[5].children[1].name == 'attr2'
        conf.children[5].children[1].value == 2

        conf.children[6].name == 'elem6'
        conf.children[6].value == 99
        conf.children[6].attributes.a == 11
        conf.children[6].attributes.b == 22
        conf.children[6].children[0].name == 'attr1'
        conf.children[6].children[0].value == 1
        conf.children[6].children[1].name == 'attr2'
        conf.children[6].children[1].value == 2

        conf.children[7].name == 'elem7'
        conf.children[7].value == [1,2,3]
        conf.children[7].attributes.a == 11
        conf.children[7].attributes.b == 22
        conf.children[7].children[0].name == 'attr1'
        conf.children[7].children[0].value == 1
        conf.children[7].children[1].name == 'attr2'
        conf.children[7].children[1].value == 2

    }
}


class MyBuilder extends BuilderExtended {

    @Override
    protected void setParent(Object parent, Object child) {
        if( !parent.children ) {
            parent.children = []
        }
        parent.children << child
    }

    @Override
    protected Object createNode(Object name) {
        def result = new Expando()
        result.name = name
        return result
    }

    @Override
    protected Object createNode(Object name, Object value) {
        def result = new Expando()
        result.name = name
        result.value = value
        return result
    }

    @Override
    protected Object createNode(Object name, Map attributes) {
        def result = new Expando()
        result.name = name
        result.attributes = attributes
        return result
    }

    @Override
    protected Object createNode(Object name, Map attributes, Object value) {
        def result = new Expando(name: name)
        result.attributes = attributes
        result.value = value
        return result
    }
}
