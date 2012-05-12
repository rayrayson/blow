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

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigList
import com.typesafe.config.ConfigValue
import org.junit.Test

class TypesafeConfigTest {

	def Config conf;
	

	
	@Test 
	def void testListConfig () {

		String CONF = 
		"""\
		list = [ 
			a 
			{ simple: nfs }
			{ nfs: { x: 1, y: 2 } }
			{ nfs: {  w: 4, z: 5 } }  
			[ a, b, c ]
		]

		"""
		

		conf = ConfigFactory.parseString(CONF)
		assert conf.hasPath("list")

		ConfigList list = conf.getList("list")
		assert list != null
		
		assert list.get(0) instanceof ConfigValue
		assert list.get(1) instanceof com.typesafe.config.ConfigObject
		assert list.get(2) instanceof com.typesafe.config.ConfigObject
		assert list.get(4) instanceof com.typesafe.config.ConfigList
		
		
		assert list.get(2).keySet().size() == 1
		assert list.get(2).get("nfs") instanceof com.typesafe.config.ConfigObject
		
		ConfigObject obj = list.get(2).get("nfs")
		assert obj.get("x").unwrapped() == 1
		assert obj.get("y").unwrapped() == 2
		
		
	} 
}
