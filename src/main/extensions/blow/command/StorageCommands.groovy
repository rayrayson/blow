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

package blow.command

import blow.BlowSession
import blow.shell.BlowShell
import blow.shell.Cmd
import blow.shell.Completion
import blow.shell.Opt
import org.jclouds.ec2.domain.Attachment
import org.jclouds.ec2.domain.Snapshot
import org.jclouds.ec2.domain.Volume

/**
 * Shell command managing EBS volumes and snapshots
 *
 * @author Paolo Di Tommaso
 */
class StorageCommands  {

    // injected by the framework
    def BlowShell shell
    def BlowSession session

    /**
     * Prints the list of volumes
     *
     * @param regionId
     *      The region for which list the volumes, if null will be listed the volumes
     *      for the current configured region
     * @param attachments
     *      If true it will print the attachment (if any) for the volume
     * @param zone
     *      The zone in which the volumes is allocated
     * @param volumeIds
     *      Shows only the volumes which matches the specified ids
     */
    @Cmd( name="listvolumes",
          summary="Display the list of volumes in the current region",
          usage="listvolumes [options] [volume-id ..]")
    @Completion( { findVolumesCompletion(it) } )

    def void listVolumes(
            @Opt(opt="r", longOpt="region", arg="region-id", description="Specify a different region")
            String regionId,
            @Opt(opt='a',longOpt='attachment', description='Include the attachments in thge report')
            Boolean attachments,
            @Opt(opt='z', longOpt='zone', description='Include the availability zone in the report')
            Boolean zone,
            List<String> volumeIds )
    {

        def list = shell.session.blockStore.listVolumes(volumeIds, regionId)

        if( !list ) {
            println "(no volumes found)"
            return
        }

        list.each() { Volume vol ->
            def line = new StringBuilder()
            line.append(vol.id).append("; ")
            line.append("${vol.size} G".padLeft(5)) .append("; ")
            line.append(vol.getCreateTime()?.format('yyyy-MM-dd HH:mm')) .append("; ")
            line.append(vol.getStatus()?.toString().padLeft(9) ) .append("; ")

            // print out the availability zone
            if( zone ) {
                line.append(vol.getAvailabilityZone()) .append('; ')
            }

            // print out the attachments if required
            if( attachments ) {
                vol.getAttachments().each { Attachment att ->

                    line.append("\n  > attached to: ")
                        .append( att.id ).append(' - ')
                        .append( att.device )
                        .append(' (').append( att.status ).append(')')
                }
            }

            println line
        }
    }

    /**
     * Find out the list of volumes starting with the provided string.
     * <p>
     * Note this method will is invoked through the {@link Completion} annotation
     *
     * @param cmdline The request volumes prefix
     * @return A list of volume IDs starting with the specified prefix
     */
    def findVolumesCompletion( String cmdline ) {
        
        def vols = shell.session.blockStore.listVolumes()
        if( cmdline ) {
            vols = vols.findAll { vol -> vol.id.startsWith(cmdline)} 
        }
        
        vols.collect { vol -> vol.id }
    } 

    /**
     * Prints the list of the snapshots
     *
     * @param printDescription
     * @param printVolume
     * @param snapshotIds
     */

    @Cmd( name="listsnapshots",
          summary="Display the list of snapshots available in the current region",
          usage="listsnapshots [options]")
    
    def void listSnapshots(

            @Opt(opt="d", longOpt="description", description="Include the snapshot 'description' in the report")
            Boolean printDescription,

            @Opt(opt="v", longOpt="volume", description="Include the associated volume-id in the report")
            Boolean printVolume,
            List<String> snapshotIds )
    {

        def list = shell.session.blockStore.listSnapshots(snapshotIds)
        if( !list ) {
            println "(no volumes found)"
            return
        }

        /*
         * print a row for each snapshots
         */
        list.each() { Snapshot snapshot ->
            def line = new StringBuilder(snapshot.id)
                    .append("; ")
                    .append(snapshot.getStartTime()?.format('yyyy-MM-dd HH:mm')) .append("; ")
                    .append(snapshot.getStatus()) .append("; ")

            /*
             * include the volume info if required
             */
            if( printVolume ) {
                def vol = snapshot.getVolumeId() ?: ""
                line.append(vol.padRight(8)).append("; ")
            }

            /*
             * include the snapshot description if required
             */
            if( printDescription ) {
                def txt = snapshot.getDescription()
                line.append( txt ? "\"$txt\"" : "")
            }

            println line
        }
    }

    /**
     * Create a new snapshot
     *
     * @param description
     *      A string value representing the snapshot description
     * @param volumeId
     *      The volume-id from which create the snapshot
     */
    @Cmd( name="createsnapshot",
          summary="Create a snapshot from given the specified EBS volume id",
          usage="createsnapshot [options] snapshot-id")

    @Completion( { findVolumesCompletion(it) } )

    def void createSnapshot(
            @Opt(opt='d', longOpt='description', len=1, description="Snapshot description (shorter than 255 chars)")
            String description,
            String volumeId
    ) {
        final ebs = shell.session.blockStore

        if( !volumeId ) {
            println "You need to specify the volume-id as command parameter"
            return
        }

        def vol = ebs.findVolume(volumeId)
        if( !vol ) {
            println "Cannot find any volume with id: '$volumeId' in region: '${session.conf.regionId}'"
            return
        }

        if( description?.length() > 255 ) {
            println "Please provide a shorter description"
            return
        }

        def answer = shell.promptYesOrNo("You are going to create a snapshot for the volume: ${volumeId}. Do you want to continue?")

        if( answer != 'y' ) { return }

        /*
         * create the snapshot
         */

        def snapshot = ebs.createSnapshot(volumeId, description, false)

        println "Creating snapshot: ${snapshot.getId()}. It can take some minutes to complete."

    }

    /**
     * Delete a snapshot store from the AWS storage
     *
     * @param snapshotId
     *      The id of the snapshot to delete
     */
    @Cmd( name='deletesnapshot',
          summary = 'Delete a AWS snapshot',
          usage='deletesnapshot snapshot-id')
    def void deletesnapshot( String snapshotId ) {
        if( !snapshotId ) {
            println "Provide on the command line the id of the snapshot to delete."
            return
        }


        def answer = shell.prompt("You are going to DELETE the snapshot '${snapshotId}'. Please enter the snapshot-id to confirm:", [snapshotId,'n'])
        if( answer != snapshotId ) {
            return
        }

        Snapshot snap = session.blockStore.deleteSnapshot(snapshotId);
        if( snap == null ) {
            print "Cannot delete snapshot '${snapshotId}'. See the log file for details."
            return
        }

        println "Snapshot schedueled for deletion. It can take some minutes."
    }


//    dev void deleteVolume( String volumeId ) {
//
//        session.blockStore.deleteVolume()
//    }

}
