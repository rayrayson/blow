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

package blow

import spock.lang.*

import com.typesafe.config.ConfigFactory;


import blow.exception.BlowConfigException;


class BlowConfigTest extends Specification {
	
	
	def "test plugin factory" ()  {
		
		setup: 	
		BlowConfig conf = new BlowConfig("")
		
		expect: 
			conf.pluginFactory != null
			conf.pluginFactory.loader != null
			//conf.pluginFactory.loader.pluginClasses.size() > 0
		
	}
	
	public void testConfiguration() {
		setup:
			def CONF = 
				"""
				access-key = abc
				secret-key = 123
				size = 1
	
				my-cluster { 
				  
					access-key = xyz
					size = 3
				}
				"""
			
		expect: 
			def conf = new BlowConfig( ConfigFactory.parseString(CONF), cluster )
			access == conf.accessKey
			secret == conf.secretKey
			size == conf.size
		
		where: 
			cluster << [ null,  "my-cluster"]
			access << [ "abc", "xyz" ]
			secret << [ "123", "123" ]
			size <<  [ 1, 3 ]
	}
	
	public void testDefaults() {

		setup: 
			def conf = new BlowConfig("")
		
		expect:	
			"us-east-1" == conf.regionId
			"us-east-1a" == conf.zoneId
			[] == conf.plugins
	} 
	
	public void testAllProperties() {
		
		setup: 
		String CONF =
			"""
			access-key = abc
			secret-key = 123
			region-id = def-region
			zone-id = def-zone
			image-id = def-image
			instance-type = def-type
			
			my-cluster {
			  
				access-key = xyz
				region-id = alpha
				zone-id = beta
				image-id = 1234
				instance-type = micro.1
				user-name = illo

				plugin = [ trace, nfs, sge ]
			}


			second-cluster {
				plugin = hosts
			}
			"""
			
		expect: 
			def conf = new BlowConfig(CONF, cluster )
			region == conf.regionId
			zone == conf.zoneId
			image == conf.imageId
			type == conf.instanceType
			user == conf.userName
			plugins == conf.plugins .collect { it.getClass().getSimpleName() }

		
		where: 
			cluster << [ "my-cluster", "second-cluster" ]
			region << [ "alpha", "def-region" ]
			zone << [ "beta", "def-zone" ]
			image << [ "1234", "def-image" ]
			type << [ "micro.1", "def-type" ]
			user << [ "illo", System.getProperty("user.name") ]
			plugins << [ [ "TracePlugin", "Nfs", "Sge" ], [ "Hosts" ]]
			
			
	}
	

	public void testPluginConfiguration () {
		
		setup: 
			String CONF = 
			"""
				my-cluster {
	
					plugin = [
						hosts,
						{nfs { path: /scratch, device: /dev/sdx }} 
						{nfs { volume-id: vol-8814f7e4, path: /data, device: /dev/sdh }} 
						{sge { root: /scratch/sge6, compile: false }}
					]
	
				}
			"""
			
			def conf = new BlowConfig(CONF, "my-cluster" )

		expect: 		
			// first plugin 
			"Hosts" == conf.plugins.get(0).getClass().getSimpleName()
			
			// second plugin
			"Nfs" ==  conf.plugins.get(1).getClass().getSimpleName()
			"/scratch" == conf.plugins.get(1).path
			"/dev/sdx" == conf.plugins.get(1).device

			// third plugin
			"Nfs" ==  conf.plugins.get(2).getClass().getSimpleName()
			"/data" == conf.plugins.get(2).path
			"/dev/sdh" == conf.plugins.get(2).device
			"vol-8814f7e4" == 	 conf.plugins.get(2).volumeId

			// fourth plugin
			"Sge" ==  conf.plugins.get(3).getClass().getSimpleName()
			"/scratch/sge6" == conf.plugins.get(3).root

	} 
	
	public void testGetConfMap() {
		
		setup: 
			
			String CONF =
			"""
			access-key = abc
			secret-key = 123
			
			my-cluster {
			  
				access-key = xyz
				region-id = alpha
				zone-id = beta
				image-id = 1234
				instance-type = micro.1
				size = 99
	
				plugin = [ nfs, sge ]
			}
	
	
			second-cluster {
				plugin = hosts
			}
			"""
			
			def conf = new BlowConfig( CONF, "my-cluster" )
			

		expect: 			
			def map = conf.getConfMap()
			map['access-key'] == 'xyz'
			map['region-id'] == 'alpha'
			map['zone-id'] == 'beta'
			map['image-id'] == '1234'
			map['size'] == 99
	
	} 
	
	

	public void testCheckValidationOK() {
		
		setup: 
			String CONF = """
				test-OK {
					secret-key: xxx
					access-key: yyy
					size: 99
					image-id: abc

					plugin = [ { nfs { path: /alpha, device: beta }} ]
				}
	
				test-FAIL {
					size: -1
				} 
			"""
			
		when:
			def conf = new BlowConfig(CONF, "test-OK" )
			conf.checkValid()

		then:
			notThrown(BlowConfigException)
	}


    public void testCheckValidationForPluginFail() {

        setup:
        String CONF = """
				test-OK {
					secret-key: xxx
					access-key: yyy
					size: 99
					image-id: abc

					plugin = [ nfs ]
				}

				test-FAIL {
					size: -1
				}
			"""

        when:
        def conf = new BlowConfig(CONF, "test-OK" )
        conf.checkValid()

        then:
        thrown(BlowConfigException)
    }
	
	
	public void testCheckValidationFail() {
		
		setup:
			String CONF = """
				test-OK {
					secret-key: xxx
					access-key: yyy
					size: 99
					image-id: abc
				}
	
				test-FAIL {
					size: -1
				}
			"""
		
		when:
			def conf = new BlowConfig( ConfigFactory.parseString(CONF), "test-FAIL" )
			conf.checkValid()
			
		then:
			thrown(BlowConfigException)
				
	}



}
