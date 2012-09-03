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
import blow.exception.CommandSyntaxException
import blow.shell.BlowShell
import blow.shell.Cmd
import blow.shell.CmdParams
import blow.shell.Completion
import com.beust.jcommander.Parameter
import groovy.util.logging.Slf4j
import org.jclouds.ec2.domain.Attachment
import org.jclouds.ec2.domain.Snapshot
import org.jclouds.ec2.domain.Volume

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
     * Parameters holder for 'listVolumes' command
     */
    static class ListVolumeParams extends CmdParams {

        @Parameter( names=['-r','--region'], description="Specify a different region" )
        String regionId;

        @Parameter(names=['-a','--attachment'], description='Include the attachments in the report')
        boolean printAttachments;

        @Parameter( names=['-z','--zone'], description='Include the availability zone in the report' )
        boolean printZone;

        @Parameter(names=['-s','--snapshot'], description="Include the snapshot from which the volume has been created")
        boolean printSnapshot;

        @Parameter(names=['-S','--filter-by-snapshot'], description='Show only volumes created by the specified spanshot-id')
        String snapshotId;

        @Parameter
        List<String> nodes

    }

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
    def void listVolumes( ListVolumeParams params )
    {

        def list = session.blockStore.listVolumes(null, params.regionId)
        def allCount = list.size()

        /*
         * filter by snapshot
         */
        if( params.snapshotId ) {
            list = list.findAll{  Volume vol -> vol.getSnapshotId() == params.snapshotId }
        }

        /*
         * The restriction by 'name' are possible only if the regionId is not specify
         */
        if( !params.regionId ) {

            def hintRequired
            def attachedVolumesId = []
            if( !params.nodes ) {
                params.nodes = new ArrayList(session.allNodes.keySet())
                hintRequired = true
            }

            params.nodes.each {
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
            if( params.printZone ) {
                line.append(vol.getAvailabilityZone()) .append('; ')
            }

            if( params.printSnapshot && vol.getSnapshotId()) {
                line.append( vol.getSnapshotId() ).append("; ")
            }


            // print out the attachments if required
            if( params.printAttachments ) {
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
     * Parameters holder for the command 'list snapshots'
     */
    static class ListSnapParams extends CmdParams {

        @Parameter(names=['-d','--description'], description="Include the snapshot 'description' in the report")
        boolean printDescription

        @Parameter(names=['-v','--volume'],  description="Include the associated volume-id in the report")
        boolean printVolume

        @Parameter
        List<String> snapshotIds
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
    
    def void listSnapshots( ListSnapParams params ) {

        def list = session.blockStore.listSnapshots(params.snapshotIds)
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
            if( params.printVolume ) {
                def vol = snapshot.getVolumeId() ?: ""
                line.append(vol.padRight(8)).append("; ")
            }

            /*
             * include the snapshot description if required
             */
            if( params.printDescription ) {
                def txt = snapshot.getDescription()
                line.append( txt ? "\"$txt\"" : "")
            }

            println line
        }
    }


    static class CreateSnapshotParams extends CmdParams {
        @Parameter(names=['-d','--description'], description="Snapshot description (shorter than 255 chars)")
        String description;

        @Parameter
        List<String> volumeId;
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

    def void createSnapshot( CreateSnapshotParams params ) {
        final ebs = session.blockStore
        final def volumeId = params.volumeId ? params.volumeId[0] : null

        if( !volumeId ) {
            throw new CommandSyntaxException('You need to specify the volume-id as command parameter')
        }

        def vol = ebs.findVolume(volumeId)
        if( !vol ) {
            println "Cannot find any volume with id: '$volumeId' in region: '${session.conf.regionId}'"
            return
        }

        if( params.description?.length() > 255 ) {
            println "Please provide a shorter description"
            return
        }

        def answer = shell.promptYesOrNo("You are going to create a snapshot for the volume: ${volumeId}. Do you want to continue?")

        if( answer != 'y' ) { return }

        /*
         * create the snapshot
         */

        def snapshot = ebs.createSnapshot(volumeId, params.description, false)

        println "Creating snapshot: '${snapshot.getId()}'. It can take some minutes to complete."

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
    def void deleteSnapshot( String snapshotId ) {
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
