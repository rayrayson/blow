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

import blow.BlowSession
import blow.util.WebHelper
import spock.lang.Specification

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class HadoopOpTest extends Specification {

    def "test init" () {
        when:
        def session = new BlowSession()
        session.conf.instanceNum = 3
        session.metadataInitialize()
        def hadoop = new HadoopOp()
        hadoop.session = session
        hadoop.initialize(null)

        then:
        hadoop.primaryNode == 'master'
        hadoop.secondaryNode == 'master'
        hadoop.jobTrackerNode == 'master'
        hadoop.slaveNodes == ['master','worker1','worker2']

    }

    def "test init (2)" () {
        when:
        def session = new BlowSession()
        session.conf.instanceNum = [master:2, worker:2]
        session.metadataInitialize()
        def hadoop = new HadoopOp()
        hadoop.session = session
        hadoop.initialize(null)

        then:
        hadoop.primaryNode == 'master1'
        hadoop.secondaryNode == 'master2'
        hadoop.jobTrackerNode == 'master2'
        hadoop.slaveNodes == ['master1','worker1','worker2']

    }

    def "test init (3)" () {
        when:
        def session = new BlowSession()
        session.conf.instanceNum = [master:3, worker:2]
        session.metadataInitialize()
        def hadoop = new HadoopOp()
        hadoop.session = session
        hadoop.initialize(null)

        then:
        hadoop.primaryNode == 'master1'
        hadoop.jobTrackerNode == 'master2'
        hadoop.secondaryNode == 'master3'
        hadoop.slaveNodes == ['master1','worker1','worker2']

    }

    def "test slaves" () {

        when:
        def session = new BlowSession()
        session.conf.instanceNum = 3
        session.metadataInitialize()
        def hadoop = new HadoopOp()
        hadoop.session = session
        hadoop.initialize(null)

        def expect = """\
        cat > ${hadoop.path}/conf/slaves << 'END_OF_FILE'
        master
        worker1
        worker2
        END_OF_FILE\
        """.stripIndent().trim()

        then:
        hadoop.confSlaves() == expect
    }



    def "test ports" () {
        when:
        def hadoop1 = new HadoopOp(primaryNode: 'hola', jobTrackerNode: 'hello', session: new BlowSession())
        def hadoop2 = new HadoopOp(primaryNode: 'the_boss:123', jobTrackerNode: 'tracker:456', session: new BlowSession())
        hadoop1.session.metadataInitialize()
        hadoop1.initialize(null)

        hadoop2.session.metadataInitialize()
        hadoop2.initialize(null)

        then:
        assert hadoop1.primaryNode == 'hola'
        assert hadoop1.jobTrackerNode == 'hello'
        assert hadoop1.secondaryNode == 'hola'

        assert hadoop2.primaryNode == 'the_boss'
        assert hadoop2.primaryNodePort == '123'

        assert hadoop2.jobTrackerNode == 'tracker'
        assert hadoop2.jobTrackerNodePort == '456'

        assert hadoop2.secondaryNode == 'the_boss'

    }


    def "test DeployList" () {
        when:
        def hadoop = new HadoopOp(session:  new BlowSession())
        hadoop.session.conf.instanceNum = 3
        hadoop.session.metadataInitialize()
        hadoop.initialize(null)

        then:
        // when there are less then 3 worker nodes, the primaryNode is added to the slaves list
        hadoop.slaveNodes == ['master', 'worker1', 'worker2']
        // bt is does not appear in the 'deploy nodes list'
        hadoop.deployNodesList == ['worker1', 'worker2']

    }

    def "test DeployList (2)" () {
        when:
        def hadoop = new HadoopOp(session:  new BlowSession())
        hadoop.session.conf.instanceNum = [master: 2, worker: 3]
        hadoop.session.metadataInitialize()
        hadoop.initialize(null)

        then:
        hadoop.primaryNode == 'master1'
        hadoop.secondaryNode == 'master2'
        hadoop.slaveNodes == ['worker1', 'worker2', 'worker3']
        hadoop.deployNodesList == ['master2', 'worker1', 'worker2', 'worker3']

    }

    def "test resource" () {
        when:
        def op = new HadoopOp()

        then:
        WebHelper.checkURLExists(op.tarball)

    }

}
