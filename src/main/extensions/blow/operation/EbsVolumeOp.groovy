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
import blow.events.OnAfterClusterStartedEvent
import blow.events.OnAfterClusterTerminationEvent
import blow.events.OnBeforeClusterStartEvent
import blow.events.OnBeforeClusterTerminationEvent
import blow.exception.BlowConfigException
import blow.exception.OperationAbortException
import blow.util.BlockStorageHelper
import blow.util.PromptHelper
import blow.util.TraceHelper
import com.google.common.eventbus.Subscribe
import groovy.util.logging.Slf4j

/**
 * Handle the installation of a AWS EBS volume
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Mixin(PromptHelper)
@Mixin(BlockStorageHelper)
@Operation('volume')
class EbsVolumeOp {

    /**
     * The volume ID of a Block Store to be mounted
     */
    @Conf("volume-id") def volumeId

    /**
     * The identifier of an existing snapshot which will be used to mount
     */
    @Conf("snapshot-id") def snapshotId

    /**
     * The path used to mount the EBS volume
     */
    @Conf def path

    /**
     * Linux device name to be used to mount the volume
     */
    @Conf def device

    /**
     * Size of
     */
    @Conf Integer size

    @Conf("delete-on-termination")
    def String deleteOnTermination = "false"

    @Conf("make-snapshot-on-termination")
    def String makeSnapshotOnTermination = "false"

    // -------------------- private section --------------------

    // the session is injected by the framework
    def BlowSession session

    def boolean quiet

    def private boolean needFormatVolume
    def private boolean needToDelete
    def private boolean makeSnapshot

    @Lazy transient private String masterInstanceId = { assert session; session.getMasterMetadata().getProviderId() } ()
    @Lazy transient private String userName = { assert session; session.conf.userName } ()


    /**
     * Validate the configuration parameters raising an exception is something is wrong
     */
    @Validate
    public void validate() {
        log.debug "Validating EBS configuration"
        assert path, "You need to define the 'path' attribute in the EBS volume configuration"
        assert path.startsWith("/"), "The volume path value must begin with a '/' character: '${path}'"

        assert !(volumeId && snapshotId), "The attribute 'volume-id' and 'snapshot-id' cannot be used at the same time"
    }

    /**
     * Make sure that the volumes and snapshot declared in the configuration file exist,
     * otherwise the cluster start operation is interrupted
     * <p>
     * This cannot be done during the 'validation' step because the {@link BlowSession} is not
     * available at that step
     *
     * @param event The {@link blow.events.OnBeforeClusterStartEvent} object
     */
    @Subscribe
    public void checkAndCreateVolume( OnBeforeClusterStartEvent event ) {

        /*
         * Check assign the device name
         */
        if( device && !session.markDevice(device) ) {
            throw new BlowConfigException("The specified device name has been already used")
        }

        device = session.getNextDevice()
        if( !device ) {
            throw new BlowConfigException("Exausted device names. Please specify the device name explicitly in the cluster configuration")
        }

        /*
         * Some sanity check on the specified 'volume-id' or 'snapshot-id'
         */
        if( snapshotId ) {
            checkSnapshot(snapshotId,session,size)
        }

        if( volumeId ) {
            checkVolume(volumeId,session)
        }


        /*
        * create a volume starting from the specified snapshot id
        */
        if( snapshotId ) {
            def vol = session.getBlockStore().createVolume(size, snapshotId)
            volumeId = vol.getId()
        }

        /*
         * create a new volume
         */
        if( !volumeId ) {
            log.debug "Creating new volume with size: $size GB"
            def vol = session.getBlockStore().createVolume(size)

            log.info "New volume created with id ${vol.getId()}, size: ${vol.getSize()} GB"
            volumeId = vol.getId()
            needFormatVolume = true // <-- set this flag to 'remember' to format the volume

        }

    }

    /**
     * Apply the NFS configuration step as soon the cluster has been created.
     * <p>
     * Mainly the following steps:
     * - Attache the EBS volume to the master node
     * - configure the master node
     * - configure the worker nodes
     *
     * @param event The {@link blow.events.OnAfterClusterStartedEvent} notified when the cluster has started
     */
    @Subscribe
    public void configureEbsVolume( OnAfterClusterStartedEvent event ) {
        if( !quiet ) {
        log.info "Configuring EBS Volume for path '${path}'"
        }

        /*
         * run the logic tracing the execution time
         */
        TraceHelper.debugTime( "Attaching EBS volume for path: ${path}", { attachBlockStore() } )
        TraceHelper.debugTime( "Mounting EBS volume for path: ${path}", { mountBlockStore() }  )
    }



    /**
     * Before terminate the cluster keep track of the Master node ID
     *
     * @param event
     */
    @Subscribe
    public void beforeTerminate( OnBeforeClusterTerminationEvent event ) {

        log.debug("Volume path: ${path}; volume: ${volumeId}; snapshot: ${snapshotId}; device: ${device}; deleteOnTermination: ${needToDelete}; makeSnapshot: ${makeSnapshot} ")

        needToDelete = (volumeId && deleteOnTermination == "true")
        makeSnapshot = (volumeId && makeSnapshotOnTermination == "true")

        /*
         * ask for the user to confirm the volume deletion
         */
        if( needToDelete ) {
            def question = "The volume ${volumeId} is going be DELETED. Do you want to continue?"
            def answer = prompt(question,['y','n','c'])
            // abort all 'terminate' operation
            if( answer == 'c' ) {
                throw OperationAbortException("Cluster termination cancelled by user")
            }

            // clear the volume delete flag
            if( answer == 'n' ) {
                needToDelete = false
            }
        }


    }

    /**
     * When the configuration flag {@link #deleteOnTermination} is {@code true}
     * delete the attached volume before destroy the current running cluster
     *
     * @param event
     */
    @Subscribe
    public void terminateAndDeleteVolume( OnAfterClusterTerminationEvent event ) {

        log.debug("Volume path: ${path}; volume: ${volumeId}; snapshot: ${snapshotId}; deleteOnTermination: ${needToDelete}; makeSnapshot: ${makeSnapshot} ")


        /*
         * Create a new snapshot before terminate
         */
        if( makeSnapshot ) {
            log.info("Creating a snapshot for volume: ${volumeId} as requested by configuration")
            def message = "Snapshot for volume ${volumeId} - Blow"
            def waitFlag = needToDelete  // wait only if we need to delete the 'volume'
            def snapshot = session.blockStore.createSnapshot(volumeId, message, waitFlag)
            log.info("Created snapshot: ${snapshot.getId()} for volume: ${volumeId}")
        }

        /*
        * Delete the volume
        */
        if( needToDelete ) {
            TraceHelper.debugTime( "Delete attached volume", { session.getBlockStore().deleteVolume(volumeId) })
        }

    }


    /**
     *  Attach the specified volume/snapshot to the 'master' node
     */
    protected void attachBlockStore() {

        assert volumeId, 'No EBS volume-id available. Something went wrong in the volume creation'
        session.getBlockStore().attachVolume( masterInstanceId, volumeId, path, device )

    }

    /**
     * Apply the 'master' configuration script
     */
    protected void mountBlockStore() {

        def runAsRoot = true
        session.runScriptOnMaster( scriptVolumeMount(), runAsRoot )

    }



    /**
     * @return The BASH script to configure the NSF on the 'master' node
     */
    protected String scriptVolumeMount( ) {

        // the format fragment required for new volumes
        String volumeFormatCommand = needFormatVolume ? "mkfs.ext3 ${device}" : "# --"

        """\
        # format ebs volume device
		${volumeFormatCommand}

		#
		# Check path
		#
		[ ! -e ${path} ] && mkdir -p ${path}
		[ -f ${path} ] && echo "The path to be export must be a directory. Make sure the path is a NOT file: '${path}'" && exit 1
		mount ${device} ${path}; sleep 1

		#
		# Assign the mounted to the current user
		#
		chown -R ${userName}:wheel ${path}
		"""
        .stripIndent()

    }

}
