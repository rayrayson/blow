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
 * Manage GlusterFS configuration
 * 
 * @author Paolo Di Tommaso
 *
 */
@Slf4j
@Mixin(PromptHelper)
@Mixin(BlockStorageHelper)
@Operation("glusterfs")
class GlusterFS {

    private static final RUN_AS_ROOT = true

    /** The list links from where download and install the GlusterFS RMP(s) */
    @Conf
    def rmp = [ "http://download.gluster.org/pub/gluster/glusterfs/3.2/3.2.4/Fedora/glusterfs-core-3.2.4-1.fc11.x86_64.rpm",
                "http://download.gluster.org/pub/gluster/glusterfs/3.2/3.2.4/Fedora/glusterfs-fuse-3.2.4-1.fc11.x86_64.rpm" ]

    /** The GlusterFS volume name */
    @Conf("volname")
    String volname = "vol1"

    /** The path on the underlying FS where the Gluster 'brick' is stored */
    @Conf("brick-path")
    String brickPath = "/gluster-data"

	/**
	 * The volume ID of a Block Store to be mounted
	 */
	@Conf("volume-id") def volumeId

	/**
	 * The identifier of an existing snapshot which will be used to mount 
	 */
	@Conf("snapshot-id") def snapshotId

	/**
	 * The GlusterFS path to be used to export the mounted volume
	 */
	@Conf def path
	
	/**
	 * Linux device name to be used to mount an EBS volume (if specified)
	 */
	@Conf String device
	
	/**
	 * Size of 
	 */
	@Conf Integer size = 10
	
	@Conf("delete-on-termination") 
	def String deleteOnTermination = "false"

    @Conf("make-snapshot-on-termination")
    def String makeSnapshotOnTermination = "false"

    /** The current session injected by the framework. Note it must defined as 'public' field */
    def BlowSession session

    // -------------------- private declarations --------------------

    private String masterInstanceId
	private String masterHostname
    private boolean needFormatVolume
    private String userName

    /** The path used to mount the EBS volume storage */
    private String blockStorageMountPath


    /**
     * Validate the configuration parameters raising an exception is something is wrong
     */
    @Validate
    public void validate() {
        log.debug "Validating GlusterFS configuration"
        assert path, "The attribute 'path' must be defined in the cluster configuration file"
        assert path.startsWith("/"), "Make sure the 'path' attribute starts with a '/' character"
        assert volname, "The attribute 'volume-name' have to be entered in the configuration file"
        assert brickPath, "The attribute 'brick-path' cannot be empty"
        assert brickPath.startsWith("/"), "The attribute 'brick-path' must start with a '/'"
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

        /*
         * If a 'snapshot' of a EBS volume id has been specified, it needs to mounted as an external device
         * In this case the attribute 'device' is required to be entered
         */
        if( snapshotId || volumeId ) {
            if( device && !session.markDevice(device) ) {
                throw new BlowConfigException("The specified device name has been already used")
            }

            device = session.getNextDevice()
            if( !device ) {
                throw new BlowConfigException("Exausted device names. Please specify the device name explicitly in the cluster configuration")
            }

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
     * Apply the GlusterFS configuration step as soon the cluster has been created.
     * <p>
     * Mainly the following steps:
     * - Attache the EBS volume to the master node
     * - configure the master node
     * - configure the worker nodes
     *
     * @param event The {@link OnAfterClusterStartedEvent} notified when the cluster has started
     */
	@Subscribe
	public void configureGluster( OnAfterClusterStartedEvent event ) {
		log.info "Configuring GlusterFS file system for shared path '${path}'"

        /*
        * configure some runtime operation attributes
        */
        this.masterInstanceId = session.getMasterMetadata().getProviderId()
        this.masterHostname = session.getMasterMetadata().getHostname()
        this.userName = session.conf.userName

        /*
         * define the mount path for the ebs volume
         */
        if( device ) {
            blockStorageMountPath = "/mnt/" + new File(device).getName()
        }

        /*
         * run the logic tracing the execution time
         */
        TraceHelper.debugTime( "GlusterFS attaching volume", { attachBlockStoreToMaster() } )
        TraceHelper.debugTime( "GlusterFS installing components", { downloadAndInstall() } )
        TraceHelper.debugTime( "GlusterFS configuring master node", { configureMaster() }  )
        TraceHelper.debugTime( "GlusterFS configuring worker nodes", { configureClients() } )
    }



    /**
     * When the configuration flag {@link #deleteOnTermination} is {@code true}
     * delete the attached volume before destroy the current running cluster
     *
     * @param event
     */
    @Subscribe
    public void deleteVolume( OnAfterClusterTerminationEvent event ) {

        /*
         * TODO since the volume detach and delete could require
         */
        def boolean needToDelete = (deleteOnTermination == "true") && ( snapshotId || volumeId );
        def boolean makeSnapshot = (volumeId && makeSnapshotOnTermination == "true")

        log.debug("DeleteVolume - path: ${path}; volume: ${volumeId}; snapshot: ${snapshotId}; deleteOnTermination: ${needToDelete}; makeSnapshot: ${makeSnapshot} ")


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

        if( !needToDelete ) {
            if( volumeId ) {
                log.info("The following volume: '${volumeId}' is still available.")
            }
            return
        }


        println "The volume ${volumeId} is going be DELETED."
        if( promptYesOrNo("Do you confirm the deletion?") != 'y' ) {
            return
        }

        /*
         * Delete the volume
         */
        TraceHelper.debugTime( "Delete attached volume: '${volumeId}", {

            session.getBlockStore().deleteVolume(volumeId)

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
            masterInstanceId = session.getMasterMetadata().getProviderId()
        }
    }

    /**
     *  Attach the specified volume/snapshot to the 'master' node
     */
    protected void attachBlockStoreToMaster() {
        log.debug("Snapshot: ${snapshotId}; Volume: ${volumeId}")

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
            log.debug("Attaching vol: ${volumeId} to instance: ${masterInstanceId}; mount path: ${blockStorageMountPath}; device: ${device}")
			session.getBlockStore().attachVolume( masterInstanceId, volumeId, blockStorageMountPath, device )
		}
		else { 
			log.debug("Nothing to attach")
		}
				
	}

    protected void downloadAndInstall() {
        session.runScriptOnNodes( getInstallScript(), null, RUN_AS_ROOT )
    }

    /**
     * Apply the 'master' configuration script
     */
	protected void configureMaster() {

        def script = getBlockStorageMount() + '\n' + getConfMaster()
		session.runScriptOnMaster( script, RUN_AS_ROOT )

	}

    /**
     * Configure the Gluster client on each node(s)
     */
	protected void configureClients() {
		session.runScriptOnNodes(getConfClient(), null, RUN_AS_ROOT)
	}


    protected String getBlockStorageMount() {
        if( !device ) {
            return ""
        }

        assert blockStorageMountPath

        """\
        # Format the EBS volume if required
        mkfs.ext3 ${device}; sleep 1

        # Mount the EBS volume if required
        [ ! -e ${blockStorageMountPath} ] && mkdir -p ${blockStorageMountPath}
        [ -f ${blockStorageMountPath} ] && echo "The path to be mounted must be a directory. Make sure the following path is NOT a file: '${blockStorageMountPath}'" && exit 1
        mount ${device} ${blockStorageMountPath}; sleep 1
        """
        .stripIndent()
    }


    /**
     * The GlusterFS install script
     * <p>
     * http://download.gluster.com/pub/gluster/glusterfs/3.2/Documentation/IG/html/chap-Installation_Guide-Installing.html
     *
     * @return The BASH script to download and install the GlusterFS RMP
     */
    protected String getInstallScript() {

        /*
         * script fragment to download the RMPs
         */
        def download = ""
        rmp.each {
            download += "wget -q ${it}\n"
        }

        /*
         * Install all of them with the 'rmp' command
         */
        def install = ""
        rmp.each { String item ->
            def name = item.substring(item.lastIndexOf("/")+1)
            install += "rpm -Uvh ${name}\n"
        }

        /*
         * compose the final script
         */
        def result = new StringBuilder()
            .append("# Install required dependencies\n")
            .append("yum -y install wget fuse fuse-libs\n")
            .append("\n")
            .append("# Download and install the Gluster components\n")
            .append(download)
            .append(install)
            .append("\n")

        return result.toString()
    }

    /**
     * Start the daemon and configure the Gluster volume
     * <p>
     * http://download.gluster.com/pub/gluster/glusterfs/3.2/Documentation/AG/html/chap-Administration_Guide-Setting_Volumes.html
     *
     *
     * @return The master node configuration script
     */
    protected String getConfMaster() {
        assert volname
        assert masterHostname
        assert brickPath
        assert brickPath.startsWith("/")

        def targetPath = blockStorageMountPath ? blockStorageMountPath + brickPath : brickPath

        """\
        # Start the Gluster daemon
        service glusterd start

        # Create a volume and start it
        gluster volume create ${volname} ${masterHostname}:${targetPath}
        gluster volume start ${volname}

        # Assign the mounted to the current user
        chown -R ${userName}:wheel ${targetPath}
        """
        .stripIndent()
    }

    /**
     * Configure the Gluster client
     * <p>
     * http://download.gluster.com/pub/gluster/glusterfs/3.2/Documentation/AG/html/chap-Administration_Guide-GlusterFS_Client.html
     *
     *
     * @return The Gluster client configuration script
     */
    protected String getConfClient() {
        assert path
        assert volname

        """\
        modprobe fuse

        # Check and create mount path
        [ ! -e ${path} ] && mkdir -p ${path}
        [ -f ${path} ] && echo "The path to be mounted must be a directory. Make sure the following path is NOT a file: '${path}'" && exit 1

        mount -t glusterfs ${masterHostname}:/${volname} ${path}
        """
        .stripIndent()
    }
}
