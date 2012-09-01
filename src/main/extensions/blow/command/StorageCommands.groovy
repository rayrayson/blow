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
import groovy.util.logging.Slf4j
import blow.exception.CommandSyntaxException

/**
 * Shell command managing EBS volumes and snapshots
 *
 * @author Paolo Di Tommaso
 */
@Slf4j
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
     * @param printAttachments
     *      If true it will print the attachment (if any) for the volume
     * @param printZone
     *      The zone in which the volumes is allocated
     * @param volumeIds
     *      Shows only the volumes which matches the specified ids
     */
    @Cmd( name="listvolumes",
          summary="Display the list of volumes in the current region",
          usage="listvolumes [options] [node name]")
    @Completion({ cmdline -> session.findMatchingAttributes(cmdline) })

    def void listVolumes(
            @Opt(opt="r", longOpt="region", arg="region-id", description="Specify a different region")
            String regionId,
            @Opt(opt='a',longOpt='attachment', description='Include the attachments in the report')
            Boolean printAttachments,
            @Opt(opt='z', longOpt='zone', description='Include the availability zone in the report')
            Boolean printZone,
            @Opt(opt='s', longOpt='snapshot', description="Include the snapshot from which the volume has been created")
            Boolean printSnapshot,
            @Opt(opt='S', longOpt='filter-by-snapshot', arg='snapshot-id', description='Show only volumes created by the specified spanshot-id')
            String snapshotId,
            List<String> nodes )
    {

        def list = shell.session.blockStore.listVolumes(null, regionId)
        def allCount = list.size()

        /*
         * filter by snapshot
         */
        if( snapshotId ) {
            list = list.findAll{  Volume vol -> vol.getSnapshotId() == snapshotId }
        }

        /*
         * The restriction by 'name' are possible only if the regionId is not specify
         */
        if( !regionId ) {

            def hintRequired
            def attachedVolumesId = []
            if( !nodes ) {
                nodes = new ArrayList(session.allNodes.keySet())
                hintRequired = true
            }

            nodes.each {
                attachedVolumesId.addAll( findAttachedVolumeIDs(it) )
            }
            list = list.findAll { Volume vol -> vol.getId() in attachedVolumesId }

            if( hintRequired && list.size() != allCount) {
                println "Hint: List of volumes in this cluster. To see all volumes available in this region use the option '--region ${session.conf.regionId}'."
            }
        }


        /*
         * any volume found
         */
        if( !list ) {
            println "(no volumes found)"
            return
        }


        /*
         * print the volumes found
         */
        list.each() { Volume vol ->
            def line = new StringBuilder()
            line.append(vol.id).append("; ")
            line.append("${vol.size} GB".padLeft(5)) .append("; ")
            line.append(vol.getCreateTime()?.format('yyyy-MM-dd HH:mm')) .append("; ")
            line.append(vol.getStatus()?.toString().padLeft(9) ) .append("; ")


            // print out the availability zone
            if( printZone ) {
                line.append(vol.getAvailabilityZone()) .append('; ')
            }

            if( printSnapshot && vol.getSnapshotId()) {
                line.append( vol.getSnapshotId() ).append("; ")
            }


            // print out the attachments if required
            if( printAttachments ) {
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

        def list = session.blockStore.listSnapshots(snapshotIds)
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
                    .append("${snapshot.getVolumeSize()}".padLeft(3) ) .append(' GB; ')
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
          summary="Create a snapshot from given the specified EBS volume-id",
          usage="createsnapshot [options] volume-id")

    @Completion( { findVolumesCompletion(it) } )

    def void createSnapshot(
            @Opt(opt='d', longOpt='description', len=1, description="Snapshot description (shorter than 255 chars)")
            String description,
            String volumeId
    ) {
        final ebs = shell.session.blockStore

        if( !volumeId ) {
            throw new CommandSyntaxException('You need to specify the volume-id as command parameter')
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
          summary = 'Delete an AWS snapshot',
          usage='deletesnapshot <snapshot-id>')
    def void deletesnapshot( String snapshotId ) {
        if( !snapshotId ) {
            throw new CommandSyntaxException('Provide on the command line the id of the snapshot to delete')
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

        println "Snapshot scheduled for deletion. It can take some minutes."
    }


    private List<String> findAttachedVolumeIDs(def criteria) {

        def result = []
        def nodes = criteria ? session.listNodes(criteria) : session.listNodes()

        nodes .each { BlowSession.BlowNodeMetadata node ->

            node.getHardware() .getVolumes() .each { org.jclouds.compute.domain.Volume vol ->
                result.add(vol.getId())
            }
        }

        return result

    }



}
