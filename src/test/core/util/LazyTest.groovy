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

package util

import spock.lang.Specification

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class LazyTest extends Specification {

    def testLazy() {
        expect:
        def p = new SimpleClassUnderTest()
        p.value == 1
    }

    def testAssigned() {
       when:
       def p = new SimpleClassUnderTest()
       p.value = 2

       then:
        p.value == 2

    }
}


class SimpleClassUnderTest {

    @Lazy
    def value = {  println "Hola";  return 1;  }()
}