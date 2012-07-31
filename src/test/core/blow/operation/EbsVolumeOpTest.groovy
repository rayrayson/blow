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

import spock.lang.Specification

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class EbsVolumeOpTest extends Specification {

    def "test validation fail" () {

        when:
        new EbsVolumeOp(path: '/hola', volumeId: 'vol-xxx', snapshotId: 'snap-yy').validate()

        then:
        thrown(AssertionError)

    }

    def "test validation ok " () {
        when:
        new EbsVolumeOp(path: '/hola').validate()
        new EbsVolumeOp(path: '/hola', snapshotId: 'snap-123').validate()
        new EbsVolumeOp(path: '/hola', volumeId: 'vol-999').validate()


        then:
        notThrown(AssertionError)
    }
}
