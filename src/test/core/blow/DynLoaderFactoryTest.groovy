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

import spock.lang.*

class DynLoaderFactoryTest extends Specification {

	
	def "test that default loader singleton exists" () 
	{ 
		when: 
			def loader = DynLoaderFactory.get();
			
		then: 
			loader != null
			loader.foundGroovySources != null
			loader.allClasses != null
			loader.shellCommands != null
			loader.pluginClasses != null
		
	}






}
