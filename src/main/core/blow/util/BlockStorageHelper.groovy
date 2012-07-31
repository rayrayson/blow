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

package blow.util

import blow.BlowSession
import blow.exception.BlowConfigException
import org.jclouds.ec2.domain.Snapshot
import groovy.util.logging.Slf4j
import org.jclouds.ec2.domain.Volume

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
class BlockStorageHelper {

    /**
     * Verify that the specified snapshot
     * @param snapshotId The snapshot unique identifier
     * @param session The current {@link BlowSession} instance
     * @throws BlowConfigException if the spanshot specified does not exist of is a status different from {@link Snapshot.Status#COMPLETED}
     */
    static def void checkSnapshot( final String snapshotId, final BlowSession session, final Integer size = null) {
        assert session
        assert snapshotId

        // method 'findSnapshot' return only the snapshot if it is allocated in the current region
        def snap = session.getBlockStore().findSnapshot(snapshotId)
        if( !snap ) {
            def msg = "Cannot find snapshot '${snapshotId}' in region '${session.conf.regionId}'"
            throw new BlowConfigException(msg)
        }

        log.debug("Snapshot: '${snap.getId()}'; status: '${snap.getStatus()}'; region: '${snap.getRegion()}'")

        if( snap.getStatus() != Snapshot.Status.COMPLETED ) {
            def msg = "Cannot use snapshot '${snapshotId}' because its current status is '${snap.getStatus()}', but it should be '${Snapshot.Status.COMPLETED}'"
            throw new BlowConfigException(msg)
        }

        if( size && size < snap.getVolumeSize() ) {
            def msg = "The volume specified size ('${size} GB') is too small for the snapshot '${snapshotId}, which requires ${snap.getVolumeSize()} GB'"
            throw new BlowConfigException(msg)
        }


    }

    /**
     * Checks that the specified {@link Volume} exists in the current availability zone and is {@link Volume.Status#AVAILABLE}
     *
     * @param volumeId The volume unique identifier
     * @param session session The current {@link BlowSession} instance
     * @throws BlowConfigException if the specified volume does not exist or it is allocated
     *   in a wrong availability zone
     */
    static def void checkVolume(String volumeId, BlowSession session ) {
        assert session
        assert volumeId

        def vol = session.blockStore.findVolume(volumeId)
        if( !vol ) {
            throw new BlowConfigException("Cannot find volume '${volumeId}' in region '${session.conf.regionId}'")
        }

        log.debug "Volume: ${volumeId}; status: '${vol.getStatus()}'; zone: '${vol.getAvailabilityZone()}'"

        if( vol.getAvailabilityZone() != session.conf.zoneId ) {
            throw new BlowConfigException("Cannot use the volume '${volumeId}' because it is allocated in the availability zone '${vol.getAvailabilityZone()}'. Only volumes in zone '${session.conf.zoneId}' can be used")
        }

        if( vol.getStatus() != Volume.Status.AVAILABLE ) {
            throw new BlowConfigException("Cannot use volume '${volumeId}' because its status is '${vol.getStatus()}', but it should be '${Volume.Status.AVAILABLE}'")
        }
    }

}
