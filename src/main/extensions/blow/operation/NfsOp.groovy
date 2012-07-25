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
import blow.util.BlockStorageHelper
import blow.util.PromptHelper
import blow.util.TraceHelper
import com.google.common.eventbus.Subscribe
import groovy.util.logging.Slf4j

/**
 * Manage NFS configuration 
 * 
 * @author Paolo Di Tommaso
 *
 */
@Slf4j
@Mixin(PromptHelper)
@Mixin(BlockStorageHelper)
@Operation("nfs")
class NfsOp {

	/**
	 * The volume ID of a Block Store to be mounted
	 */
	@Conf("volume-id") def volumeId

	/**
	 * The identifier of an existing snapshot which will be used to mount
	 */
	@Conf("snapshot-id") def snapshotId

	/**
	 * The NFS path to be used to export the mounted volume	
	 */
	@Conf def path
	
	/**
	 * Linux device name to be used to mount the volume
	 */
	@Conf def device
	
	/**
	 * Size of 
	 */
	@Conf Integer size = 10
	
	@Conf("delete-on-termination") 
	def String deleteOnTermination = "false"

    @Conf("make-snapshot-on-termination")
    def String makeSnapshotOnTermination = "false"

	private String masterInstanceId 
	private String userName
	private String masterHostname
    private boolean needFormatVolume

	private BlowSession session

    /**
     * Validate the configuration parameters raising an exception is something is wrong
     */
    @Validate
    public void validate() {
        log.debug "Validating NFS configuration"
        assert path, "You need to define the 'path' attribute in the NFS configuration"
    }

    /**
     * Make sure that the volumes and snapshot declared in the configuration file exist,
     * otherwise the cluster start operation is interrupted
     * <p>
     * This cannot be done during the 'validation' step because the {@link BlowSession} is not
     * available at that step
     *
     * @param event The {@link OnBeforeClusterStartEvent} object
     */
    @Subscribe
    public void  sanityCheck( OnBeforeClusterStartEvent event ) {

        def BlowSession session = event.session

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
         * check the snapshot
         */
        if( snapshotId ) {
            checkSnapshot(snapshotId,session)
        }

        /*
         * check the volumeId exists and it is allocated in the same availability zone
         */
        if( volumeId && volumeId != "new" ) {
            checkVolume(volumeId,session)
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
     * @param event The {@link OnAfterClusterStartedEvent} notified when the cluster has started
     */
	@Subscribe
	public void configureNFS( OnAfterClusterStartedEvent event ) {
		log.info "Configuring NFS file system for shared path '${path}'"

        /*
         * configure some runtime operation attributes
         */
        this.session = event.session;
        this.masterInstanceId = session.getMasterMetadata().getProviderId()
        this.masterHostname = session.getMasterMetadata().getHostname()
        this.userName = session.conf.userName

        /*
         * run the logic tracing the execution time
         */
        TraceHelper.debugTime( "NFS attaching volume", { attachBlockStoreToMaster() } )
        TraceHelper.debugTime( "NFS configuring master node", { configureMaster() }  )
        TraceHelper.debugTime( "NFS configuring worker nodes", { configureWorkers() } )
    }



    /**
     * When the configuration flag {@link #deleteOnTermination} is {@code true}
     * delete the attached volume before destroy the current running cluster
     *
     * @param event
     */
    @Subscribe
    public void deleteVolume( OnAfterClusterTerminationEvent event ) {

        def boolean needToDelete = (deleteOnTermination == "true") && ( snapshotId || volumeId );
        def boolean makeSnapshot = (volumeId && makeSnapshotOnTermination == "true")

        log.debug("Nfs path: ${path}; volume: ${volumeId}; snapshot: ${snapshotId}; deleteOnTermination: ${needToDelete}; makeSnapshot: ${makeSnapshot} ")


        /*
         * Create a new snapshot before terminate
         */
        if( makeSnapshot ) {
            log.info("Creating a snapshot for volume: ${volumeId} as requested by configuration")
            def message = "Snapshot for volume ${volumeId} - Blow"
            def waitFlag = needToDelete  // wait only if we need to delete the 'volume'
            def snapshot = event.session.blockStore.createSnapshot(volumeId, message, waitFlag)
            log.info("Created snapshot: ${snapshot.getId()} for volume: ${volumeId}")
        }

        if( !needToDelete ) {
            if( volumeId ) {
                log.info("The following volume: '${volumeId}' is still available.")
            }
            return
        }


        if( !volumeId ) {
            log.warn("I'm sorry. I don't know which is the volume-id for snapshot '${snapshotId}'")
            return
        }


        /*
         * Delete the volume
         */
        TraceHelper.debugTime( "Delete attached volume", {

            event.session.getBlockStore().deleteVolume(volumeId)

        })

    }

    /**
     * Before terminate the cluster keep track of the Master node ID
     *
     * @param event
     */
    @Subscribe
    public void fetchMasterId( OnBeforeClusterTerminationEvent event ) {
        if( !masterInstanceId ) {
            masterInstanceId = event.session.getMasterMetadata().getProviderId()
        }

        // it is required to find out the volume-id for which create a snapshot
        if( snapshotId && !volumeId && makeSnapshotOnTermination == "true" ) {
            // TODO: note that the device can be generated by framework
            // so if the application is stopped there's no guarantee that it will be the same
            volumeId = session.getBlockStore().findAttachedVolume(masterInstanceId, device) ?. getId()
            log.debug("Found attached volume '${volumeId}' for device '${device}' and created from snapshot '${snapshotId}' ")
        }
    }

    /**
     *  Attach the specified volume/snapshot to the 'master' node
     */
    protected void attachBlockStoreToMaster() {
		
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
		if( volumeId == "new" ) {
            log.debug "Creating new volume with size: $size G"
			def vol = session.getBlockStore().createVolume(size)

            log.info "New volume created with id ${vol.getId()}, size: ${vol.getSize()} G"
            volumeId = vol.getId()
            needFormatVolume = true // <-- set this flag to 'remember' to format the volume

		}

		/*
		 * now attach the volume
		 */
		if( volumeId ) {
			session.getBlockStore().attachVolume(masterInstanceId, volumeId, path, device )
		}
		else { 
			log.debug("Nothing to attach")
		}
				
	}

    /**
     * Apply the 'master' configuration script
     */
	protected void configureMaster() {

		def mountDevice = ( volumeId ) ? true : false
		def runAsRoot = true
		session.runScriptOnMaster( scriptMaster(mountDevice), runAsRoot)

	}

    /**
     * Apply the client configuration script to each 'worker'
     */
	protected void configureWorkers() {

		if( session.conf.size < 2 ) { /* nothing to do */ return }
		
		session.runScriptOnWorkers(scriptWorker(), true)
	}

    /**
     * @param mountDevice When {@code true} mount the external device (it must be an EBS volume)
     * @return The BASH script to configure the NSF on the 'master' node
     */
	protected String scriptMaster( boolean mountDevice ) {

        // the format fragment required for new volumes
        String volumeFormatCommand
        if( needFormatVolume ) {
            volumeFormatCommand = """\
            # format device
            mkfs.ext3 ${device}
            sleep 1
            """
            .stripIndent()
        }


		// Mount the external device (EBS block) only is required 
		// otherwise it will mount a local path
		String mountDeviceCommand = (mountDevice) ? "mount ${device} ${path}; sleep 1" : ""


		"""\
		# Installing nfs components 
		yum install -y nfs-utils rpcbind
		
		# disable selinux 
		setenforce 0

		${volumeFormatCommand}
		
		#
		# Check path
		#
		[ ! -e ${path} ] && mkdir -p ${path}
		[ -f ${path} ] && echo "The path to be export must be a directory. Make sure the path is a NOT file: '${path}'" && exit 1
		${mountDeviceCommand}
		
		#
		# Assign the mounted to the current user
		#
		chown -R ${userName}:wheel ${path}
		
		#
		# Exporting the shared FS
		#
		echo "${path}	*(rw,async,no_root_squash,no_subtree_check)" >> /etc/exports
		exportfs -ra 
		
		#
		# Configuring services
		#
		service rpcbind start
		service nfs start
		service nfslock start
		chkconfig --level 2345 rpcbind on
		chkconfig --level 2345 nfs on
		chkconfig --level 2345 nfslock on
		""" 
		.stripIndent()
		
	}

    /**
     * @return The NFS configuration script to be executed on the 'worker' nodes
     */
	protected String scriptWorker() {
	
		"""\
		# Installing nfs components 
		yum install -y nfs-utils rpcbind
		
		# disable selinux 
		setenforce 0
		
		
		#
		# Configuring services
		#
		service rpcbind start
		service nfslock start
		chkconfig --level 2345 rpcbind on
		chkconfig --level 2345 nfslock on
		
		#
		# Create mount point and mount it 
		# 
		mkdir -p ${path}
		echo "${masterHostname}:${path}      ${path}      nfs     rw,hard,intr    0 0" >> /etc/fstab
		mount -a
		"""	
		.stripIndent()
		
	} 
}