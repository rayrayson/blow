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

import spock.lang.Specification

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class InjectorHelperTest extends Specification {

    def "test inject" () {

        when:
        def obj = new Super()
        def now = new Date()
        InjectorHelper.inject(Super, obj, ['hola', 99, now])

        then:
        obj.num == 99
        obj.date == now
        obj.str == 'hola'
        obj.checkStr() == 'hola'
        obj.otherString == 'hola'

    }
}


class Simple {

    private String str

    public Integer num

    public String getStr() { str }

    public String checkStr() { str }

}

class Super extends Simple {

    private Date date

    def String otherString

}
