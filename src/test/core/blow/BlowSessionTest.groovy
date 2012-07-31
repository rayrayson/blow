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

import blow.operation.EbsVolumeOp
import spock.lang.Specification

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BlowSessionTest extends Specification {

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
        setup:
        def CONF =
            """
				access-key = abc
				secret-key = 123
				size = 1

				my-cluster {
					access-key = xyz
					size = 3
					operations = [ {volume { path: /here }} ]
				}
				"""

        when:
        def session = new BlowSession(new BlowConfig( CONF, "my-cluster" ), "my-cluster")
        def file = session.persist()

        // read back
        def serializedSession =  BlowSession.read('my-cluster')

        then:
        file.exists()
        serializedSession.confHashCode == session.conf.hashCode()
        serializedSession.conf.operations.get(0).getClass() == EbsVolumeOp.class
        (serializedSession.conf.operations.get(0) as EbsVolumeOp) .session. is( serializedSession )


        cleanup:
        file?.delete()

    }

}
