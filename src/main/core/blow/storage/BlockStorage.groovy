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

package blow.storage

import groovy.util.logging.Log4j
import org.jclouds.compute.ComputeServiceContext
import org.jclouds.ec2.domain.Attachment
import org.jclouds.ec2.domain.Volume
import org.jclouds.ec2.services.ElasticBlockStoreClient

import java.util.concurrent.TimeoutException

import blow.BlowConfig

/**
 * Abstraction on (Amazon) Block Store 
 * 
 * @author Paolo Di Tommaso
 *
 */

@Log4j
class BlockStorage {
	
	final ComputeServiceContext context;
	
	final BlowConfig conf
	
	@Lazy
	private ElasticBlockStoreClient ebs = context.getProviderSpecificContext().getApi() .getElasticBlockStoreServices()
	
	/**
	 * Block store handler constructor
	 * 
	 * @param context The reference to the current {@link ComputeServiceContext} object 
	 * @param conf The reference to the current {@link BlowConfig} object
	 */
	BlockStorage(ComputeServiceContext context, BlowConfig conf ) {
		this.context = context	
		this.conf = conf
	}
	
	
	/**
	* Attach an existing EBS volume to the specified a running vm instance
	*
	* @param nodeId the node to which attach the volume, use the Amazon instance-id
	* @param volumeId the Amazon provided volume-id
	* @param mountPath the FS path to which mount the volume
	* @param device the linux device to which attach the volume
	*
	*/
   def attachVolume( String nodeId, String volumeId, String mountPath = "/data", String device = "/dev/sdh" ) {
	   // now attach a EBS volume
       blow.storage.BlockStorage.log.debug "Attaching vol: $volumeId to instance: $nodeId"
	   Attachment attachment = ebs.attachVolumeInRegion(conf.regionId, volumeId, nodeId, device)

	   Volume vol
	   def status = attachment.getStatus()
	   // TODO add a timeout constraint
	   while( status != Attachment.Status.ATTACHED ) {
		   blow.storage.BlockStorage.log.debug "Waiting the volume to be attached ($status)"
		   sleep(30000)
		   // query for the attachment status
		   vol = ebs.describeVolumesInRegion(conf.regionId, volumeId).find({true})
		   attachment = vol.getAttachments().find( { it.getDevice() == device } )
		   // TODO handle for null
		   status = attachment.getStatus()
	   }
	   
	   if( !vol ) { 
		   vol = vol.getAttachments().find( { it.getDevice() == device } )
	   }
	   
	   return vol
   }
   
   /**
	* Create a new volume in the current availability zone
	*
	* @param size The new volume size in GB
	* @return
	*/
   def createVolume( int size, String snapshotId = null ) {
	   
	  Volume vol
	  
	  /*
	   * create a volume from the specified 'snapshot' and the specified 'size' 
	   */
	  if( snapshotId && size ) {
		  vol = ebs.createVolumeFromSnapshotInAvailabilityZone( conf.zoneId, size, snapshotId )
	  }
	  /*
	   * create a volume from the specified 
	   */
	  else if( snapshotId ) {
		  vol = ebs.createVolumeFromSnapshotInAvailabilityZone( conf.zoneId, snapshotId )
	  }
	  
	  else {
		  
		  if( !size ) {
			  blow.storage.BlockStorage.log.info("Volume size not specified. Applying default size: 10G")
		  	  size = 10
		  }
		  
		  vol = ebs.createVolumeInAvailabilityZone( conf.zoneId, size )
	  }

	  /*
	   * Wait for the volume to be ready
	   */
	  vol = waitForVolumeAvail( vol )
   }
   
   /**
    * Find all the volumes attached to the specified {@code instanceId} 
    * 
    * @param instanceId The instance ID to which the volume is attached
    * @patam device The logical device to which the volume is attached
    * @return The found {@link Volume} of {@code null} if nothing is found for the specified parameters
    */
   def Volume findAttachedVolume( String instanceId, String device ) {
	
	   ebs.describeVolumesInRegion( conf.regionId ).find {
		   Volume vol -> vol.status == Volume.Status.IN_USE  \
		   && vol.attachments?.find { Attachment attach -> attach.instanceId == instanceId && attach.device == device }
	   }

   }
   
   def void deleteVolume( String instanceId, String device, String volumeId, String snapshotId ) {
	   
	   blow.storage.BlockStorage.log.debug "Deleting volume attached to: '$device' for instance: '${instanceId}' "
	   
	   /*
	   * lookup for the volume to delete
	   */
	  def vol = findAttachedVolume(instanceId, device)
	  if( vol == null) {
		  blow.storage.BlockStorage.log.warn "No volume found for instance-id: '$instanceId' and device: '$device'."
		  return
	  }
	  
	  /*
	   * double check before delete the volume
	   */
	  if( !volumeId ) { 
		  volumeId = vol.getId()
	  }
	  else if( volumeId != vol.getId() ){
		  blow.storage.BlockStorage.log.warn "Volume id (${vol.getId()}) does not match with the declared one (${volumeId}). Volume deleting skipped.";
		  return
	  }
	  
	  if( snapshotId && snapshotId != vol.getSnapshotId() ) {
		  blow.storage.BlockStorage.log.warn "Snapshot id (${vol.getSnapshotId()}) does not match with the declared one (${snapshotId}) for volume: {volumeId}. Volume deleting skipped.";
		  return
		  }
	  
	  /*
	   * Before detach the volume 
	   * Note: detaching EBS volume could take a lot of time
	   */
	 blow.storage.BlockStorage.log.debug "Detaching volume: '${volumeId}' "
	 ebs.detachVolumeInRegion(conf.regionId, volumeId, true, null)
	 try {
		 waitForVolumeAvail(vol, 10 * 60 * 1000)
         blow.storage.BlockStorage.log.debug "Deleting volume: '${volumeId}' "
		 ebs.deleteVolumeInRegion( conf.regionId, volumeId )
	 }
	 catch( TimeoutException e ) {
	 	blow.storage.BlockStorage.log.warn( e.getMessage() );
         blow.storage.BlockStorage.log.warn("Detaching volume: '${volumeId}' is requiring too much time. Volume has not been deleted. YOU WILL HAVE TO DELETE IT MANUALLY!!")
	 }
	 
	   
   }
   
   
   def waitForVolumeAvail( Volume vol, long timeout = 5 * 60 * 1000 ) {
	   
	  /*
	   * check for a valid status
	   */
	  def startTime = System.currentTimeMillis();
	  while( vol.getStatus() != Volume.Status.AVAILABLE && (System.currentTimeMillis()-startTime < timeout) ) {
		  blow.storage.BlockStorage.log.debug "Vol status: ${vol.getStatus()}"
		  sleep(25000)
		  vol = ebs.describeVolumesInRegion(conf.regionId, vol.getId()).find({true})
	  }
	  
	  if( vol.getStatus() != Volume.Status.AVAILABLE ) {
		  def message = "Volume: '${vol.getId()}' is in a inconsistent status: '${vol.getStatus()}' (expected '${Volume.Status.AVAILABLE}')"
		  throw new TimeoutException(message)
	  }
	  
	  return vol
   }
   


	
}
