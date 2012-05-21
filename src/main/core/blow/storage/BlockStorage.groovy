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

package blow.storage

import blow.BlowConfig
import groovy.util.logging.Slf4j
import org.jclouds.aws.AWSResponseException
import org.jclouds.compute.ComputeServiceContext
import org.jclouds.ec2.domain.Attachment
import org.jclouds.ec2.domain.Snapshot
import org.jclouds.ec2.domain.Volume
import org.jclouds.ec2.options.CreateSnapshotOptions
import org.jclouds.ec2.options.DescribeSnapshotsOptions
import org.jclouds.ec2.services.ElasticBlockStoreClient

import java.util.concurrent.TimeoutException

/**
 * Abstraction on (Amazon) Block Store 
 * 
 * @author Paolo Di Tommaso
 *
 */

@Slf4j
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
       log.debug "Attaching vol: $volumeId to instance: $nodeId"
	   Attachment attachment = ebs.attachVolumeInRegion(conf.regionId, volumeId, nodeId, device)

	   Volume vol
	   def status = attachment.getStatus()
	   // TODO add a timeout constraint
	   while( status != Attachment.Status.ATTACHED ) {
		   log.debug "Waiting the volume to be attached ($status)"
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
          log.debug "Creating volume from snapshot ${snapshotId} with size $size G"
		  vol = ebs.createVolumeFromSnapshotInAvailabilityZone( conf.zoneId, size, snapshotId )
	  }
	  /*
	   * create a volume from the specified 
	   */
	  else if( snapshotId ) {
          log.debug "Creating volume from snapshot ${snapshotId} with default size"
		  vol = ebs.createVolumeFromSnapshotInAvailabilityZone( conf.zoneId, snapshotId )
	  }
	  
	  else {

		  if( !size ) {
			  log.debug("Volume size not specified, using default value")
		  	  size = 10
		  }

          log.debug "Creating new volume with size $size G"
		  vol = ebs.createVolumeInAvailabilityZone( conf.zoneId, size )
	  }

	  /*
	   * Wait for the volume to be ready
	   */
	  vol = waitForVolumeAvail( vol )
   }

    def createSnapshot( String volumeId, String description, boolean waitForCompleted = true ) {
        assert volumeId, "The argument 'volumeId' cannot be empty"


        def options = []
        if( description ) {
            options.add(CreateSnapshotOptions.Builder.withDescription(description))
        }

        def snapshot = ebs.createSnapshotInRegion(conf.regionId, volumeId, options as CreateSnapshotOptions[] )

        if( !waitForCompleted ) {
            return snapshot
        }

        return waitForSnapshotCompleted(snapshot)
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

   def void deleteVolume( String volumeId ) {
       assert volumeId

       Volume vol = ebs.describeVolumesInRegion(conf.regionId, volumeId).find()
       assert vol, "Cannot find volume: ${volumeId} in region: ${conf.regionId}"

       log.debug("Delete volume: '${volumeId}' - current status: ${vol.getStatus().toString()}")

       /*
        * Detach the volume if it is used
        */

       if( vol.getStatus() == Volume.Status.IN_USE ) {

           log.debug "Detaching volume: '${volumeId}' "
           ebs.detachVolumeInRegion(conf.regionId, volumeId, false, null)
           try {
               waitForVolumeAvail(vol, 10 * 60 * 1000)
           }
           catch( TimeoutException e ) {
               log.warn( e.getMessage() );
               log.warn("Detaching volume: '${volumeId}' is requiring too much time. Volume has not been deleted. YOU WILL HAVE TO DELETE IT MANUALLY!!")
           }
       }
       else {
           log.debug("Volume: '${volumeId}' was in status:  '${vol.getStatus().toString()}' so it was not detached")
       }


       if( vol.getStatus() == Volume.Status.AVAILABLE ) {
           log.debug "Deleting volume: '${volumeId}' "
           ebs.deleteVolumeInRegion( conf.regionId, volumeId )
       }
       else if( vol.getStatus() == Volume.Status.DELETING ) {
           log.debug("Volume ${volumeId} is in DELETING status ")
       }
       else {
           log.warn("Volume ${volumeId} is in a wrong status: '${vol.getStatus().toString()}'. CANNOT DELETE IT.")
       }

   }

   def void deleteVolume( String instanceId, String device, String volumeId, String snapshotId ) {
	   
	   log.debug "Deleting volume attached to: '$device' for instance: '${instanceId}' "
	   
	   /*
	   * lookup for the volume to delete
	   */
	  def vol = findAttachedVolume(instanceId, device)
	  if( vol == null) {
		  log.warn "No volume found for instance-id: '$instanceId' and device: '$device'."
		  return
	  }
	  
	  /*
	   * double check before delete the volume
	   */
	  if( !volumeId ) { 
		  volumeId = vol.getId()
	  }
	  else if( volumeId != vol.getId() ){
		  log.warn "Volume id (${vol.getId()}) does not match with the declared one (${volumeId}). Volume deleting skipped.";
		  return
	  }
	  
	  if( snapshotId && snapshotId != vol.getSnapshotId() ) {
		  log.warn "Snapshot id (${vol.getSnapshotId()}) does not match with the declared one (${snapshotId}) for volume: {volumeId}. Volume deleting skipped.";
		  return
	  }
	  
	  /*
	   * Before detach the volume 
	   * Note: detaching EBS volume could take a lot of time
	   */
	 log.debug "Detaching volume: '${volumeId}' "
	 ebs.detachVolumeInRegion(conf.regionId, volumeId, true, null)
	 try {
		 waitForVolumeAvail(vol, 10 * 60 * 1000)
         log.debug "Deleting volume: '${volumeId}' "
		 ebs.deleteVolumeInRegion( conf.regionId, volumeId )
	 }
	 catch( TimeoutException e ) {
	 	log.warn( e.getMessage() );
        log.warn("Detaching volume: '${volumeId}' is requiring too much time. Volume has not been deleted. YOU WILL HAVE TO DELETE IT MANUALLY!!")
	 }
	 
	   
   }

//    def Volume deleteVolumeNoWait( String volumeId ) {
//
//        Volume vol = findVolume(volumeId)
//        if( !vol ) return null
//
//        if( vol.getStatus() == Volume.Status.)
//        /*
//         * Before detach the volume
//         * Note: detaching EBS volume could take a lot of time
//         */
//        log.debug "Detaching volume: '${volumeId}' "
//        ebs.detachVolumeInRegion(conf.regionId, volumeId, true, null)
//        try {
//            waitForVolumeAvail(vol, 10 * 60 * 1000)
//            log.debug "Deleting volume: '${volumeId}' "
//            ebs.deleteVolumeInRegion( conf.regionId, volumeId )
//        }
//        catch( TimeoutException e ) {
//            log.warn( e.getMessage() );
//            log.warn("Detaching volume: '${volumeId}' is requiring too much time. Volume has not been deleted. YOU WILL HAVE TO DELETE IT MANUALLY!!")
//        }
//
//    }
//
   
   def waitForVolumeAvail( Volume vol, long timeout = 5 * 60 * 1000 ) {
	   
	  /*
	   * check for a valid status
	   */
	  def startTime = System.currentTimeMillis();
	  while( vol.getStatus() != Volume.Status.AVAILABLE && (System.currentTimeMillis()-startTime < timeout) ) {
		  log.debug "Vol status: ${vol.getStatus()}"
		  sleep(25000)
		  vol = ebs.describeVolumesInRegion(conf.regionId, vol.getId()).find({true})
	  }
	  
	  if( vol.getStatus() != Volume.Status.AVAILABLE ) {
		  def message = "Volume: '${vol.getId()}' is in a inconsistent status: '${vol.getStatus()}' (expected '${Volume.Status.AVAILABLE}')"
		  throw new TimeoutException(message)
	  }
	  
	  return vol
   }


    def waitForSnapshotCompleted( Snapshot snap, long timeout = 5 * 60 * 1000 ) {

        /*
         * check for a valid status
         */
        def startTime = System.currentTimeMillis();
        while( snap.getStatus() != Snapshot.Status.COMPLETED && (System.currentTimeMillis()-startTime < timeout) ) {
            log.debug "Snapshot status: ${snap.getStatus()}"
            sleep(25000)
            def opt = DescribeSnapshotsOptions.Builder.snapshotIds(snap.getId())
            snap = ebs.describeSnapshotsInRegion(conf.regionId, opt).find()
        }

        if( snap?.getStatus() != Snapshot.Status.COMPLETED ) {
            def message = "Waiting for COMPLETED status for snapshot '${snap.getId()}' but it is in a unexpected status: '${snap.getStatus()}'"
            throw new TimeoutException(message)
        }

        return snap
    }

    /**
     * @return The list of all volumes in the current region
     */
   def Set<Volume> listVolumes(List<String> volumeIds=null, String regionId=null) {
       if( !regionId ) {
            regionId = conf.regionId
       }

       try {
            ebs.describeVolumesInRegion( regionId, volumeIds as String[] )
       }
       catch( AWSResponseException e ) {
           log.debug("Error on listVolumes method", e)
           return null
       }
   }


    /**
     * Find out the information for the specified volume id
     * @param volumeId The unique ID for the volume e.g. vol-12345
     * @return The {@link Volume} instance for the specified volume on {@code null} if does not exist
     */
    def Volume findVolume(String volumeId) {
        try {
            return ebs.describeVolumesInRegion(conf.regionId, volumeId)?.find()
        }
        catch( AWSResponseException e ) {
            log.debug("Error on findVolume method", e)
            return null
        }

    }


    /**
     * @return The list of all snapshots in the current region
     */
    def Set<Snapshot> listSnapshots(List<String> snapshotIds ) {

        def options = []

        if( snapshotIds ) {
            options.add(DescribeSnapshotsOptions.Builder.snapshotIds(snapshotIds as String[]))
        }
        else if( conf.accountId ) {
            options.add(DescribeSnapshotsOptions.Builder.ownedBy(conf.accountId))
        }

        ebs.describeSnapshotsInRegion( conf.regionId, options as DescribeSnapshotsOptions[] )
    }


    /**
     * Find out the information for the specified snapshot id
     * @param volumeId The unique ID for the volume e.g. vol-12345
     * @return The {@link Volume} instance for the specified volume on {@code null} if does not exist
     */
    def Snapshot findSnapshot(String snapshotId) {
        assert snapshotId, "Argument 'snapshotId' cannot be empty"

        try {
            def opt = DescribeSnapshotsOptions.Builder.snapshotIds(snapshotId)
            ebs.describeSnapshotsInRegion(conf.regionId, opt)?.find()
        }
        catch( AWSResponseException e ) {
            log.debug("Error on 'findSnapshot'", e)
            return null
        }
    }


    def Snapshot deleteSnapshot( String snapshotId ) {
        log.debug("Deleting snapshot: '$snapshotId'")

        Snapshot snap = findSnapshot(snapshotId)
        if( snap ) {
            ebs.deleteSnapshotInRegion(conf.regionId, snapshotId)
            return snap
        }

        return null
    }

}
