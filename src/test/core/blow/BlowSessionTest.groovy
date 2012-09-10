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

import blow.builder.BlowConfigBuilder
import blow.operation.EbsVolumeOp
import org.jclouds.domain.Location
import org.jclouds.domain.LoginCredentials
import org.jclouds.domain.ResourceMetadata
import spock.lang.Specification
import org.jclouds.compute.domain.*

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BlowSessionTest extends Specification {


    def "test listNodes" () {
        when:
        def session = new BlowSession()
        session.conf.instanceNum = 5
        session.metadataInitialize()

        then:
        session.listNodesNames('master') == ['master']
        session.listNodesNames('worker') == ['worker1','worker2','worker3','worker4']
        session.listNodesNames() == ['master','worker1','worker2','worker3','worker4']

    }

    def "test initMetadata "() {
        when:
        def session = new BlowSession()
        session.conf.roles = ['a', 'b']
        session.conf.instanceNum = [a:1, b:3]

        session.metadataInitialize()
        then:
        session.allNodes.containsKey('a')
        session.allNodes.containsKey('b1')
        session.allNodes.containsKey('b2')
        session.allNodes.containsKey('b3')
        session.allNodes.containsKey('b4') == false
    }

    def "test getNextDevice" () {

        when:
        def session = new BlowSession()

        then:
        session.getNextDevice() == "/dev/sdf"
        session.getNextDevice() == "/dev/sdg"
        session.getNextDevice() == "/dev/sdh"

    }


    def "test markDevice" () {


        when:
        def session = new BlowSession()
        session.getNextDevice()

        then:
        session.markDevice("/dev/sdf") == false
        session.markDevice("/dev/xvdh") == true
        session.getNextDevice() == "/dev/sdg"
        session.getNextDevice() == "/dev/sdi"
        session.getNextDevice() == "/dev/sdj"

    }

    def testSaveReadSession() {

        when:
        def config = new BlowConfigBuilder('myCluster') . _ {
            accessKey 'xyz'

            myCluster {
                operations {
                    volume(path: '/root')
                }
            }

        }

        def session = new BlowSession(config, 'myCluster')
        def file = session.persist()
        println "saved file:" + file
        println "operations: " + config.operations
        // read back
        def serializedSession =  BlowSession.read('myCluster')

        then:
        file.exists()
        serializedSession.confHashCode == session.conf.hashCode()
        serializedSession.conf.operations.get(0).getClass() == EbsVolumeOp.class
        (serializedSession.conf.operations.get(0) as EbsVolumeOp) .session. is( serializedSession )


        cleanup:
        file?.delete()

    }


    def "test metadata" () {
        when:
        def session = new BlowSession()
        session.conf.roles = ['node']
        session.conf.instanceNum = 100
        session.metadataInitialize()

        session.nodeNamesMap.put('node001', '1')
        session.nodeNamesMap.put('node002', '2')
        session.nodeNamesMap.put('node003', '3')

        session.nodeRolesMap.put('master', '1')
        session.nodeRolesMap.put('worker', '2')
        session.nodeRolesMap.put('worker', '3')

        session.allNodes.put('node001', session.wrapNode(new MyNode(id: '1', ip: '1.1.1.1')))
        session.allNodes.put('node002', session.wrapNode(new MyNode(id: '2', ip: '2.2.2.2')))
        session.allNodes.put('node003', session.wrapNode(new MyNode(id: '3', ip: '3.3.3.3')))

        then:
        // master
        session.allNodes['node001'].getId() == '1'
        session.allNodes['node001'].getNodeName() == 'node001'
        session.allNodes['node001'].getNodeIp() == '1.1.1.1'
        session.allNodes['node001'].getNodeRole() == 'master'

        // worker 1
        session.allNodes['node002'].getId() == '2'
        session.allNodes['node002'].getNodeName() == 'node002'
        session.allNodes['node002'].getNodeIp() == '2.2.2.2'
        session.allNodes['node002'].getNodeRole() == 'worker'

        // worker 2
        session.allNodes['node003'].getId() == '3'
        session.allNodes['node003'].getNodeName() == 'node003'
        session.allNodes['node003'].getNodeIp() == '3.3.3.3'
        session.allNodes['node003'].getNodeRole() == 'worker'
    }


    def "test metadata refresh" () {
        when:
        def session = new BlowSession()
        session.conf.roles = ['node']
        session.conf.instanceNum = 10
        session.metadataInitialize()

        session.nodeNamesMap.put('node01', '1')
        session.nodeNamesMap.put('node02', '2')
        session.nodeNamesMap.put('node03', '3')

        session.nodeRolesMap.put('master', '1')
        session.nodeRolesMap.put('worker', '2')
        session.nodeRolesMap.put('worker', '3')

        session.allNodes.put('node01', session.wrapNode(new MyNode(id: '1', imageId: 'a123', ip: '1.1.1.1')))
        session.allNodes.put('node02', session.wrapNode(new MyNode(id: '2', imageId: 'a123', ip: '2.2.2.2')))
        session.allNodes.put('node03', session.wrapNode(new MyNode(id: '3', imageId: 'a123', ip: '3.3.3.3')))

        def newSet = [ new MyNode(id: '1', imageId: 'a123', ip: '1.1.1.1'), new MyNode(id: '3', imageId: 'a321', ip: '3.3.3.4') ]
        session.metadataUpdate( newSet )


        then:
        session.allNodes.size() == 10

        // master
        session.allNodes['node01'].getId() == '1'
        session.allNodes['node01'].getNodeName() == 'node01'
        session.allNodes['node01'].getNodeIp() == '1.1.1.1'
        session.allNodes['node01'].getNodeRole() == 'master'
        session.allNodes['node01'].getImageId() == 'a123'

        session.allNodes['node02'] == null

        // worker 2
        session.allNodes['node03'].getId() == '3'
        session.allNodes['node03'].getNodeName() == 'node03'
        session.allNodes['node03'].getNodeIp() == '3.3.3.4'
        session.allNodes['node03'].getNodeRole() == 'worker'
        session.allNodes['node03'].getImageId() == 'a321'
    }

    def "test getNextName"() {

        when:
        def session = new BlowSession()
        session.conf.instanceNum = 10

        session.nodeRolesMap.put( 'worker', '2' )
        session.nodeRolesMap.put( 'worker', '3' )

        then:
        session.getNextNodeName('master') == 'master'
        session.getNextNodeName('worker') == 'worker3'

    }


    def "test getNextName_2"() {

        when:
        def session = new BlowSession()
        session.conf.instanceNum = ['master':3, 'slave':100 ]
        session.conf.roles = ['master','slave']

        session.nodeRolesMap.put( 'master', '1' )
        session.nodeRolesMap.put( 'slave', '2' )
        session.nodeRolesMap.put( 'slave', '3' )
        session.nodeRolesMap.put( 'slave', '4' )
        session.nodeRolesMap.put( 'slave', '5' )

        then:
        session.getNextNodeName('master') == 'master2'
        session.getNextNodeName('slave') == 'slave005'

    }


    def "test findNodeIDs" () {
        when:
        def session = new BlowSession()
        session.nodeNamesMap.put('master', '1')
        session.nodeNamesMap.put('slave1', '2')
        session.nodeNamesMap.put('slave2', '3')
        session.nodeNamesMap.put('slave3', '4')
        session.nodeNamesMap.put('slave4', '5')

        session.nodeRolesMap.put( 'master', '1' )
        session.nodeRolesMap.put( 'slave', '2' )
        session.nodeRolesMap.put( 'slave', '3' )
        session.nodeRolesMap.put( 'slave', '4' )
        session.nodeRolesMap.put( 'slave', '5' )

        then:
        session.findNodeIDs('master') == ['1']
        session.findNodeIDs('slave') == ['2','3','4','5']
        session.findNodeIDs(['master','slave']) == ['1','2','3','4','5']
        session.findNodeIDs(['master','slave'] as String[]) == ['1','2','3','4','5']

        session.findNodeIDs('slave1') == ['2']
        session.findNodeIDs(['master','slave1']) == ['1','2']
    }


    def "test findNodeIDs (2)" () {
        when:
        def session = new BlowSession()
        session.conf.roles = ['master']
        session.conf.instanceNum

        session.nodeNamesMap.put('master1', '1')
        session.nodeNamesMap.put('master2', '2')

        session.nodeRolesMap.put( 'master', '1' )
        session.nodeRolesMap.put( 'master', '2' )

        then:
        session.findNodeIDs('master1') == ['1']
        session.findNodeIDs('master2') == ['2']
        session.findNodeIDs('master') == ['1','2']
    }


}

class MyNode implements  NodeMetadata {

    def id

    def imageId

    def hostname

    def group

    def ip


    @Override
    String getId() {
        return id
    }

    @Override
    String getHostname() {
        return hostname
    }

    @Override
    String getGroup() {
        return group
    }

    @Override
    Hardware getHardware() {
        return null
    }

    @Override
    String getImageId() {
        return imageId
    }

    @Override
    OperatingSystem getOperatingSystem() {
        return null
    }

    @Override
    NodeState getState() {
        return null
    }

    @Override
    int getLoginPort() {
        return null
    }

    @Override
    String getAdminPassword() {
        return null  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    LoginCredentials getCredentials() {
        return null  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    Set<String> getPublicAddresses() {
        return new HashSet<String>([ip])
    }

    @Override
    Set<String> getPrivateAddresses() {
        return null  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    ComputeType getType() {
        return null  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    String getProviderId() {
        return id
    }

    @Override
    String getName() {
        return null  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    Location getLocation() {
        return null  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    URI getUri() {
        return null  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    Map<String, String> getUserMetadata() {
        return null  //To change body of implemented methods use File | Settings | File Templates.
    }


    @Override
    Set<String> getTags() {
        return null  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    int compareTo(ResourceMetadata<ComputeType> o) {
        return 0  //To change body of implemented methods use File | Settings | File Templates.
    }
}

