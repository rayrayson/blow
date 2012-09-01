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

import blow.BlowConfig
import blow.exception.BlowConfigException
import spock.lang.Specification

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class EbsVolumeOpTest extends Specification {

    def "test validation ok " () {
        when:
        def conf = new BlowConfig(userName: 'pablo')
        new EbsVolumeOp(path: '/hola', applyTo: 'master').validate(conf)
        new EbsVolumeOp(path: '/hola', supply: 'snap-123', applyTo: 'master').validate(conf)
        new EbsVolumeOp(path: '/hola', supply: 'vol-999', applyTo: 'master').validate(conf)


        then:
        notThrown(AssertionError)
    }

    def "test checkEphemeral FAIL" () {

        when:
        EbsVolumeOp.checkEphemeral('ephemeral0','t1.micro')

        then:
        thrown( BlowConfigException )

    }

    def "test checkEphemeral FAIL (2)" () {

        when:
        EbsVolumeOp.checkEphemeral('ephemeral1','m1.small')

        then:
        thrown( BlowConfigException )

    }

    def "test checkEphemeral OK" () {

        when:
        EbsVolumeOp.checkEphemeral('ephemeral0','m1.small')
        EbsVolumeOp.checkEphemeral('ephemeral0','m1.medium')
        EbsVolumeOp.checkEphemeral('ephemeral1','m1.large')
        EbsVolumeOp.checkEphemeral('ephemeral3','m1.xlarge')

        then:
        notThrown( BlowConfigException )

    }

}
