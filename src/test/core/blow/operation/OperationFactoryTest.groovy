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

package blow.operation;


import blow.DynLoader
import blow.TestOperation
import spock.lang.Shared
import spock.lang.Specification

public class OperationFactoryTest extends Specification {

	@Shared
	def loader;
	
	def setupSpec() {
		loader = new DynLoader("./src/test/extensions")
	}
	
	def testCreate() {

		setup:
			// note: this class is created using the name declared by the annotation 'WithName' 
			// see class 'TestPlugin'
			def plugin = new OperationFactory(loader).create("my-super-operation")

		expect :
			plugin != null
				
	}
	

	

    def testOperationHashCode() {

        when:
        def op1 = new TestOperation()
        op1.baseValue = 0
        op1.value1 = 1
        op1.value2 = "due"

        def op2 = new TestOperation()
        op2.baseValue = 0
        op2.value1 = 1
        op2.value2 = "due"



        then:
            OperationHelper.opHashCode(op1) == OperationHelper.opHashCode(op2)


    }
	

}
