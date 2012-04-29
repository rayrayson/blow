/*
 * Copyright (c) 2012. Paolo Di Tommaso
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

package blow

import spock.lang.*;

class DynLoaderTest extends Specification {

	def "create loader with file array" () {
		
		when: 
			def loader = new DynLoader(new File("./hola"), new File("./ciao"))
		
		then: 
			loader.roots.size() == 2
			loader.roots[0] == new File("./hola")
			loader.roots[1] == new File("./ciao")
	}
	
	def "create loader with string array" () {

		when: 
			def loader = new DynLoader("./hola", "./ciao")
		
		then: 
			loader.roots.size() == 2
			loader.roots[0] == new File("./hola")
			loader.roots[1] == new File("./ciao")
			
	}
	
	def "create with empty constructor"() {
	
		expect: 
			loader.roots.size() == 0

		where: 
			loader << [ new DynLoader(), new DynLoader([] as File[]), new DynLoader([] as String[]) ]
				
	} 
	
	
	def "load all classes"() {
	
		when:
			def loader = new DynLoader("./src/test/extensions");
			
		then: 
			loader.foundGroovySources.size() == 7
			loader.allClasses.size() == 7
			loader.allClasses*.getSimpleName() == [ "Command1", "Command2", "Plugin1", "TestPlugHolder", "TestPlugin", "TestPluginBase", "TestShellMethods" ]
			
			loader.shellCommands.size() == 2
			loader.shellCommands*.getSimpleName() == ["Command1", "Command2"]
		
			loader.pluginClasses.size() == 3
			loader.pluginClasses*.getSimpleName() == ["Plugin1", "TestPlugHolder", "TestPlugin" ]

            loader.shellMethods *. getName() == ['command1', 'command3']
	}

}
