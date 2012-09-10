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

package blow;

import blow.exception.BlowConfigException
import spock.lang.Specification
import blow.builder.BlowConfigBuilder

class BlowConfigTest extends Specification {

    def "test regionId" () {
        when:
        def conf1 = new BlowConfig(regionId:'eu-west-1')
        def conf2 = new BlowConfig(regionId: 'ap-northeast-1')
        def conf3 = new BlowConfig(regionId: 'xxx')

        then:
        conf1.regionId == 'eu-west-1'
        conf1.zoneId == 'eu-west-1a'

        conf2.regionId == 'ap-northeast-1'
        conf2.zoneId == 'ap-northeast-1a'

        conf3.regionId == 'xxx'
        conf3.zoneId == new BlowConfig().zoneId
    }

    def "test zoneId" () {
        when:
        def conf1 = new BlowConfig()
        def conf2 = new BlowConfig(zoneId: 'ap-northeast-1c')
        def conf3 = new BlowConfig(zoneId: 'eu-west-1b')
        def conf4 = new BlowConfig(zoneId: 'xxx')

        then:
        conf1.regionId == 'us-east-1'
        conf1.zoneId == 'us-east-1a'

        conf2.regionId == 'ap-northeast-1'
        conf2.zoneId == 'ap-northeast-1c'

        conf3.regionId == 'eu-west-1'
        conf3.zoneId == 'eu-west-1b'

        conf4.regionId == conf1.regionId
        conf4.zoneId == 'xxx'

    }


    def "test instanceType" () {
        when:
        def conf = new BlowConfig()
        conf.instanceType = [master:'m1', worker: 't2']

        then:
        conf.instanceTypeFor('master') == 'm1'
        conf.instanceTypeFor('worker') == 't2'
    }

    def "test instanceType (2)" () {
        when:
        def conf = new BlowConfig()
        conf.roles = ['master','worker']
        conf.instanceType = 'xyz'

        then:
        conf.instanceTypeFor('master') == 'xyz'
        conf.instanceTypeFor('worker') == 'xyz'
    }

    def "test instanceNum" () {
        when:
        def conf = new BlowConfig()
        conf.instanceNum = [master:2, worker: 20]

        then:
        conf.instanceNumFor('master') == 2
        conf.instanceNumFor('worker') == 20
    }

    def "test instanceNum (2)" () {
        when:
        def conf = new BlowConfig()
        conf.roles = ['master','brick','worker']
        conf.instanceNum = 10

        then:
        conf.instanceNumFor('master') == 1
        conf.instanceNumFor('brick') == 1
        conf.instanceNumFor('worker') == 8
    }

    def "test imageId" () {
        when:
        def conf = new BlowConfig()
        conf.imageId = [master:'xxx', worker: 'yyy']

        then:
        conf.imageIdFor('master') == 'xxx'
        conf.imageIdFor('worker') == 'yyy'
    }

    def "test imageId (2)" () {
        when:
        def conf = new BlowConfig()
        conf.roles = ['master','worker']
        conf.imageId = 'xyz'

        then:
        conf.imageIdFor('master') == 'xyz'
        conf.imageIdFor('worker') == 'xyz'
    }


    def "test defult roles" () {
        when:
        def conf = new BlowConfig()

        then:
        assert conf.masterRole == 'master'
        assert conf.workersRole == 'worker'
    }


    def "test default roles (2)" () {
        when:
        def conf = new BlowConfig()
        conf.masterRole = 'a'
        conf.workersRole = 'b'

        then:
        assert conf.masterRole == 'a'
        assert conf.workersRole == 'b'
    }


    def "test default roles (3)" () {
        when:
        def conf = new BlowConfig()
        conf.roles = ['x','y','z']

        then:
        assert conf.masterRole == 'x'
        assert conf.workersRole == 'y'
    }



    public void testCheckValidationOK() {
		
		setup: 
			String CONF = """
				testOK {
					secretKey 'xxx'
					accessKey 'yyy'
					instanceNum 99
					imageId 'abc'

					operations {
					    nfs( path: '/alpha', device: 'beta' )
					}
				}
	
				testFAIL {
					instanceNum: -1
				} 
			"""
			
		when:
			def builder = BlowConfigBuilder.create( CONF )
            def conf = builder.buildConfig( 'testOK' )
			conf.checkValid()

		then:
			notThrown(BlowConfigException)
	}


    public void testCheckValidationForPluginFail() {

        setup:
        String CONF = """
				testOK {
					secretKey 'xxx'
					accessKey 'yyy'
					instanceNum 99
					imageId 'abc'

					operations {
					    nfs()
					}
				}

				testFAIL {
				    userName 'pippo'
					instanceNum 99
				}
			"""

        when:
        def conf = BlowConfigBuilder.create(CONF).buildConfig('testFAIL')
        conf.checkValid()

        then:
        thrown(BlowConfigException)
    }
	
	


    public void testGetPortsArray() {
        expect:
        BlowConfig.getPortsArrays( '80' ) == [80]
        BlowConfig.getPortsArrays( '80,81,8080' ) == [80,81,8080]
        BlowConfig.getPortsArrays( '80,81,90-94,8080,9000-9003' ) == [80,81,90,91,92,93,94,8080,9000,9001,9002,9003]
    }


}
