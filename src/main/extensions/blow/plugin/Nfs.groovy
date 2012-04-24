/*
 * Copyright (c) 2012. Paolo Di Tommaso
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

package blow.plugin

import blow.BlowSession
import blow.events.OnAfterClusterCreateEvent
import blow.events.OnAfterClusterTerminationEvent
import blow.util.TraceHelper
import com.google.common.eventbus.Subscribe
import groovy.util.logging.Log4j
import blow.events.OnBeforeClusterTerminationEvent

/**
 * Manage NFS configuration 
 * 
 * @author Paolo Di Tommaso
 *
 */
@Log4j
@Plugin("nfs")
class Nfs {

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
	@Conf def device = "/dev/sdh";
	
	/**
	 * Size of 
	 */
	@Conf Integer size = 10
	
	@Conf("delete-on-termination") 
	def boolean deleteOnTermination = "false"
	

	private String masterInstanceId 
	private userName 
	private masterHostname
	
	private BlowSession pilot


    @Validate
    public void validate() {

        assert path, "You need to define the 'path' attribute in the NFS configuration"
        assert device, "You need to define the 'device' attribute in the NFS configuraton"

    }


	@Subscribe
	public void configureNFS( OnAfterClusterCreateEvent event ) {
		
		TraceHelper.debugTime( "Configure NFS",  { doConfigureNFS(event.session) } )
		
	}

	protected void doConfigureNFS( BlowSession session ) {
		this.pilot = session;
		
		
		/*
		* configure some runtime plugin attributes
		*/
	   
	   masterInstanceId = session.getMasterMetadata().getProviderId()
	   masterHostname = session.getMasterMetadata().getHostname()
	   userName = session.conf.userName
	   
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

        /*
         * TODO since the volume detach and delete could require
         */
        def boolean needToDelete = deleteOnTermination == true && ( snapshotId || volumeId );

        if( !needToDelete ) {  return }

        TraceHelper.debugTime( "Delete attached volume", {

            event.session.getBlockStore().deleteVolume(masterInstanceId, device, volumeId, snapshotId)

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
    }


    protected void attachBlockStoreToMaster() {
		
		/*
		 * create a volume starting from the specified snapshot id 
		 */
		if( snapshotId ) {
			def vol = pilot.getBlockStore().createVolume(size, snapshotId)
			volumeId = vol.getId()
		}
		
		/*
		 * create a new volume
		 */
		if( volumeId == "new" ) {
			def vol = pilot.getBlockStore().createVolume(size)
			volumeId = vol.getId()
		}

		/*
		 * now attach the volume
		 */
		if( volumeId ) {
			pilot.getBlockStore().attachVolume(masterInstanceId, volumeId, path, device )
		}
		else { 
			blow.plugin.Nfs.log.debug("Nothing to attach")
		}
				
	}
	
	protected void configureMaster() {

		def mountDevice = ( volumeId ) ? true : false
		def runAsRoot = true
		pilot.runScriptOnMaster( scriptMaster(mountDevice), runAsRoot)

	}
	
	protected void configureWorkers() {

		if( pilot.conf.size < 2 ) { /* nothing to do */ return }
		
		pilot.runScriptOnWorkers(scriptWorker(), true)
	}



	protected String scriptMaster( boolean mountDevice ) {
		
		// Mount the external device (EBS block) only is required 
		// otherwise it will mount a local path
		String mountDeviceCommand = (mountDevice) ? "mount ${device} ${path}; sleep 1" : ""
		
		"""\
		# Installing nfs components 
		yum install -y nfs-utils rpcbind
		
		# disable selinux 
		setenforce 0
		
		#
		# Check path
		#
		[ ! -e ${path} ] && mkdir -p ${path}
		[ -f ${path} ] && echo "The path to be export must be a directory. Check if the following path is a file: '${path}'" && exit 1
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