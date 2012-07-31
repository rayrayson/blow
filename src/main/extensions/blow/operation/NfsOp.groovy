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
class NfsOp  {

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


    /* ---------- private declarations ------------- */

    /** The current session object */
    def BlowSession session

    /** Delegate volume operation */
    private EbsVolumeOp volume

	private String masterHostname

    private String userName


    /**
     * Validate the configuration parameters raising an exception is something is wrong
     */
    @Validate
    public void validate() {
        log.debug "Validating NFS configuration"
        assert path, "You need to define the 'path' attribute in the NFS configuration"
        assert path.startsWith("/"), "The volume path value must begin with a '/' character: '${path}'"

        if( snapshotId || volumeId ) {
            volume = new EbsVolumeOp()
            volume.device = this.device
            volume.snapshotId = this.snapshotId
            volume.volumeId = this.volumeId != "new" ? this.volumeId : null // <-- note: for new volume
            volume.path = this.path
            volume.size = this.size
            volume.deleteOnTermination = this.deleteOnTermination
            volume.makeSnapshotOnTermination = this.makeSnapshotOnTermination
            volume.quiet = true  // <-- less info printing when embedding in this operation

            // validate the volume
            volume.validate()
        }

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

        if( volume ) {
            volume.session = this.session
            volume.checkAndCreateVolume(event)
        }

        userName = session.conf.userName

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

        /* mount the volume if required */
        if( volume ) {
            volume.configureEbsVolume(event)
        }

        /*
         * the master host nae
         */
        masterHostname = session.getMasterMetadata().getHostname()


        /*
         * run the logic tracing the execution time
         */
        TraceHelper.debugTime( "NFS configuring master node", { configureMaster() }  )
        TraceHelper.debugTime( "NFS configuring worker nodes", { configureWorkers() } )
    }


    @Subscribe
    public void beforeTerminate( OnBeforeClusterTerminationEvent event ) {
        if( volume ) {
            volume.beforeTerminate(event)
        }
    }

    @Subscribe
    public void afterTerminate( OnAfterClusterTerminationEvent event ) {
        if( volume ) {
            volume.terminateAndDeleteVolume(event)
        }
    }



    /**
     * Apply the 'master' configuration script
     */
	protected void configureMaster() {

		def runAsRoot = true
        def script = scriptMaster()

        // prepend the script fragment for path creation
        if( !volume ) {
            script = makePath() + script
        }

		session.runScriptOnMaster( script, runAsRoot )

	}

    /**
     * Apply the client configuration script to each 'worker'
     */
	protected void configureWorkers() {

		if( session.conf.size < 2 ) { /* nothing to do */ return }
		
		session.runScriptOnWorkers(scriptWorker(), true)
	}


    protected String makePath () {
        assert path
        assert userName

        """\
		#
		# Check the path
		#
		[ ! -e ${path} ] && mkdir -p ${path}
		[ -f ${path} ] && echo "The path to be export must be a directory. Make sure the path is a NOT file: '${path}'" && exit 1

		#
		# Assign the mounted to the current user
		#
		chown -R ${userName}:wheel ${path}
        """
    }

    /**
     * @return The BASH script to configure the NSF on the 'master' node
     */
	protected String scriptMaster( ) {
        assert path

		"""\
		# Installing nfs components 
		yum install -y nfs-utils rpcbind
		
		# disable selinux 
		setenforce 0

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
        assert path
        assert masterHostname

	
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
