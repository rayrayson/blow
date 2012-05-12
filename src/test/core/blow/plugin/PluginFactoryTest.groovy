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

package blow.plugin;


import blow.TestPlugHolder
import blow.TestPlugin
import spock.lang.Shared
import spock.lang.Specification

import java.lang.reflect.Field
import blow.DynLoader

public class PluginFactoryTest extends Specification {

	@Shared
	def loader;
	
	def setupSpec() {
		loader = new DynLoader("./src/test/extensions")
	}
	
	def testCreate() {

		setup:
			// note: this class is created using the name declared by the annotation 'WithName' 
			// see class 'TestPlugin'
			def plugin = new PluginFactory(loader).create("my-super-plugin")

		expect :
			plugin != null
				
	}
	
	
	public void testCreateWithConf() {

		when:
			String str =
			"""
			value1 = 10
			value-2 = 20
			basevalue = hola
			missing-value = nope
			"""
		
		
		then:
			def plugin = new PluginFactory(loader).create("TestPlugin", str)
			plugin != null
			plugin.value1 == 10
			plugin.value2 == "20"
			plugin.basevalue == "hola"
		
	}

    def void testCreateWithConfHolder() {

        when:
        def conf = """\
        ALPHA=1
        BETA=true
        DELTA=three
        """

        def plugin = new PluginFactory(loader).create(TestPlugHolder.class.getSimpleName(), conf )

        then:
            plugin.map != null
            plugin.map.ALPHA == 1
            plugin.map.BETA == true
            plugin.map.DELTA == "three"

    }
	
	public void testCreateWithStringConf() {
		
		setup: 
			def pp = new PluginFactory(loader).create("testplugin", "value1: 99")
			
		expect:
			pp.value1 == 99
		
		
	} 
	

	
	def testLookupConfFields() {
		when:
		List<Field> fields = PluginFactory.getConfFields( TestPlugin.class )
		List<String> names = []
		fields.each {  names.add( it.name ) }

		then:
		[ "basevalue", "value1", "value2"] == names.sort()
				
	} 
	
	def void testGetConfProps() {

		when:
		Map<String,String> fields = PluginFactory.getConfProperties( TestPlugin.class )
		
		then:
		"value1" == fields["value1"]
		"value2" == fields["value-2"]
		"basevalue" == fields["basevalue"]
		
	}
	

}
