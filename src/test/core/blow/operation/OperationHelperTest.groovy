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

package blow.operation

import spock.lang.Specification
import java.lang.reflect.Field
import blow.TestOperation

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class OperationHelperTest extends Specification {

    def "test opName" () {
        expect:
        OperationHelper.opName( SimpleOp.class ) == "SimpleOp"
        OperationHelper.opName( new SimpleOp() ) == "SimpleOp"
        OperationHelper.opName( AnnotatedOp.class ) == "hola"
        OperationHelper.opName( new AnnotatedOp() ) == "hola"

    }


    def "test toString" () {
        expect:
        OperationHelper.opToString( new SimpleOp() ) == "SimpleOp()"
        OperationHelper.opToString( new AnnotatedOp() ) == "hola()"
        OperationHelper.opToString( new AnnotatedOp(val1: 'uno', val2:'due')) == "hola( val1: 'uno', val2: 'due' )"
    }



    def testLookupConfFields() {
        when:
        List<Field> fields = OperationHelper.getConfFields( TestOperation.class )
        List<String> names = []
        fields.each {  names.add( it.name ) }

        then:
        [ "baseValue", "value1", "value2"] == names.sort()

    }


}




class SimpleOp { }

@Operation('hola')
class AnnotatedOp {

    @Conf val1 = 1
    @Conf val2 = 2

}