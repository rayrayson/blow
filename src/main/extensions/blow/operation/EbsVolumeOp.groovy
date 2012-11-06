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

import blow.BlowConfig
import blow.BlowSession
import blow.exception.BlowConfigException
import blow.util.PromptHelper
import blow.util.TraceHelper
import com.google.common.eventbus.Subscribe
import groovy.util.logging.Slf4j
import org.jclouds.aws.ec2.compute.AWSEC2TemplateOptions
import org.jclouds.ec2.domain.Snapshot
import org.jclouds.ec2.domain.Volume

import java.util.regex.Matcher

import blow.events.*

/**
 * Handle the installation of a AWS EBS volume
 *
 * Read more
 * http://docs.amazonwebservices.com/AWSEC2/latest/UserGuide/block-device-mapping-concepts.html
 *
 * http://docs.amazonwebservices.com/AWSEC2/latest/UserGuide/RootDeviceStorage.html
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Mixin(PromptHelper)
@Operation('volume')
class EbsVolumeOp {

    /**
     * The resource name to use to create this volume.
     * Valid values are:
     * - volume identifier e.g. vol-xxx
     * - snapshot identifier e.g. snap-xxx
     * - ephemeral virtual device e.g. ephemeral0, ephemeral1, etc
     */
    @Conf
    String supply

    /**
     * The path used to mount the EBS volume
     */
    @Conf
    String path

    /**
     * Linux device name to be used to mount the volume
     */
    @Conf
    String device

    /**
     * Size of
     */
    @Conf
    Integer size

    @Conf
    def boolean deleteOnTermination

    @Conf
    def boolean makeSnapshotOnTermination

    @Conf
    String fsType = "ext3"


    /**
     * The node to which attach the EBS
     */
    @Conf
    def String applyTo

    // -------------------- private section --------------------
    def boolean quiet

    // the session is injected by the framework
    private BlowSession session

    private boolean needFormatVolume
    private String volumeId
    private String snapshotId
    private String ephemeralId
    private String userName
    private int affectedNodes = 1

    /*
     * Verify Ephemeral storage mapping
     * Read more http://docs.amazonwebservices.com/AWSEC2/latest/UserGuide/InstanceStorage.html#StorageOnInstanceTypes
     */
    transient private static final ephemeralAvailByType = [
            't1.micro': 0,
            'm1.small': 1,
            'm1.medium': 1,
            'm1.large': 2,
            'm1.xlarge': 4,
            'c1.medium': 1,
            'c1.xlarge': 4,
            'm2.xlarge': 1,
            'm2.2xlarge': 1,
            'm2.4xlarge': 2,
            'hi1.4xlarge': 2,
            'cc1.4xlarge': 2,
            'cc2.8xlarge': 4,
            'cg1.4xlarge': 2
    ]


    /**
     * Validate the configuration parameters raising an exception is something is wrong
     */
    @Validate
    public void validate(BlowConfig conf) {
        log.debug "Validating EBS configuration"
        assert path, "You need to define the 'path' attribute in the EBS volume configuration"
        assert path.startsWith("/"), "The volume path value must begin with a '/' character: '${path}'"

        /*
         * Check the storage type
         */
        if( !supply ) {
             // OK
        }
        else if( supply.startsWith('vol-') ) {
            volumeId = supply
        }
        else if( supply.startsWith('snap-') ) {
            snapshotId = supply
        }
        else if( supply ==~ /ephemeral\d/ ) {
            ephemeralId = supply
        }
        else {
            throw new BlowConfigException("Invalid 'supply' property value: '${supply}'")
        }


        /*
         * Validate the 'applyTo' attribute
         */
        if( !applyTo ) {
            applyTo = conf.masterRole
        }

        // how many nodes are affected by this operation
        // if 'applyTo' is a 'role' name -> the number of instances in that role
        // otherwise 1 (by default)
        if( applyTo in conf.roles ) {
            affectedNodes = conf.instanceNumFor(applyTo)
        }

        /*
         * Check the user name
         */
        userName = conf.userName
        if( !userName ) {
            throw new BlowConfigException("Missing 'userName' property in configuration file")
        }

        log.debug "Volume validate -- path: ${path}; device: $device; size: ${size?:'-'}; supply: ${supply?:'-'} "
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
    public void beforeClusterStart( OnBeforeClusterStartEvent event ) {

        /*
         * Check assign the device name
         */
        if( device && !session.markDevice(device) ) {
            throw new BlowConfigException("The specified device name has been already used")
        }

        if( !device ) {
            device = session.getNextDevice()
        }

        if( !device ) {
            throw new BlowConfigException("Exausted device names. Please specify the device name explicitly in the cluster configuration")
        }

        log.debug "Volume beforeCreate -- path: ${path}; device: $device; size: ${size?:'-'}; supply: ${supply?:'-'} "


        /*
         * Some sanity check on the specified 'volume-id' or 'snapshot-id'
         */
        if( snapshotId ) {
            checkSnapshot(snapshotId)
        }

        /*
         * check the volumeId
         */
        if( volumeId ) {
            checkVolume(volumeId)
            // TO DO checks that 'applyTo' refers to one and only one node
        }

        if( ephemeralId ) {
            // TODO make it generic so that it is able to handle 'role' and 'node' names
            def type = session.conf.instanceTypeFor(applyTo)
            checkEphemeral(ephemeralId, type)

            if( deleteOnTermination ) {
                log.warn "Property 'deleteOnTermination' is ignored by ephemeral device"
                deleteOnTermination = false
            }

            if( makeSnapshotOnTermination ) {
                log.warn "Property 'makeSnapshotOnTermination' is ignored by ephemeral device"
                makeSnapshotOnTermination = false
            }

            if( size ) {
                log.warn "Property 'size' is ignored by ephemeral device ($size)"
            }
        }


        /*
         * This requires some explanation:
         * There are four possible "type" of volume:
         * 1) Created by a snapshotId
         * 2) Created New
         * 3) Attached providing a volumeId
         * 4) Ephemeral
         *
         * The option 1,2 and 4 three are created via device mapping by the template options,
         * so it have to be applied to all the nodes in the same role.
         * The volume attached by ID (3rd) can be applied only to one node.
         *
         * Moreover the attribute 'applyTo' can be used to specify a 'role' or single 'node' name.
         *
         * So when 'applyTo' refers to single node within a role with more than one node,
         * the following case applies:
         * 1) In the case of the 'ephemeral' volume, it is not possible (since it need to be create by
         *    the template options) so report an error
         *
         * 2) When the volumeId has been provide OK
         *
         * 3) In the other cases (new volume, or created bu a snapshot) create a volume and
         *    follow like the point 2
         *
         */

        if( affectedNodes>1 && volumeId ) {
            throw new BlowConfigException("EBS volume: '${volumeId}' cannot be attached to more than one instance for role: '${applyTo}'")
        }

        def singleNode = affectedNodes == 1 && !session.conf.roles.contains(applyTo)
        if( singleNode && ephemeralId ) {
            throw new BlowConfigException("Ephemeral volume '${ephemeralId}' cannot be attached to a single node in the role: '$applyTo'. ")
        }

        if( affectedNodes == 1 && singleNode && !volumeId ) {
            createVolume()
        }

    }


    // ----------------------------------------------------------------------------------
    //  Events

    /**
     * Before launch the node map the ephemeral block
     * @param event
     */
    @Subscribe
    def void beforeNodeLaunch( OnBeforeNodeLaunchEvent event ) {

        if( !applyTo ?. startsWith(event.role) ) {
            return
        }

        if( volumeId ) {
            // not supported, so skip
            return
        }

        /*
         * OK let's mapping
         */

        def opt = (event.options as AWSEC2TemplateOptions)
        def message

        if( ephemeralId ) {
            opt.mapEphemeralDeviceToDeviceName(device, ephemeralId)
            message = "Mapping EBS '$ephemeralId' to path '$path' for '$applyTo'"
            needFormatVolume = true  // <-- it turns out that some ephemeral volume for some instance types must be formatted
        }
        else if( snapshotId ) {
            opt.mapEBSSnapshotToDeviceName(device,snapshotId, size, deleteOnTermination)
            message = "Mapping EBS snapshot '$snapshotId' with size ${size} GB to path '$path' for '$applyTo'"
        }
        else {
            size = size ?: 10
            opt.mapNewVolumeToDeviceName(device, size, deleteOnTermination)
            message = "Mapping new EBS volume with size $size GB to path '$path' for '$applyTo'"
            needFormatVolume = true // <-- flag to have the new volume formatted before the 'mount' step
        }

        log.debug message
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
    public void afterClusterStarted( OnAfterClusterStartedEvent event ) {
        if( !quiet ) {
            log.info "Mounting EBS volume for path: ${path} "
        }
        /*
         * run the logic tracing the execution time
         */
        if( volumeId ) {
            TraceHelper.debugTime( "Attaching EBS volume for path: ${path}" ) { attachBlockStore() }
        }

        TraceHelper.debugTime( "Mounting EBS volume for path: ${path}" ) { mountBlockStore() }
    }



    /**
     * Before terminate the cluster keep track of the Master node ID
     *
     * @param event
     */
    @Subscribe
    public void beforeClusterTerminate( OnBeforeClusterTerminationEvent event ) {

        log.debug("Volume path: ${path}; volume: ${volumeId}; snapshot: ${snapshotId}; device: ${device}; deleteOnTermination: ${deleteOnTermination}; makeSnapshot: ${makeSnapshotOnTermination} ")


        /*
         * Make volumes snapshots if required
         */
        def listOfVolumes

        if( makeSnapshotOnTermination ) {
            if( volumeId ) {
                listOfVolumes = [volumeId]
            }
            else {
                listOfVolumes = findAttachedVolumeIds()
            }

        }

        if( listOfVolumes ) {
            log.debug "Creating snapshots for the following volumes: $listOfVolumes"
            listOfVolumes .each  { String volumeId -> makeSnapshot( volumeId ) }
        }

    }

    /**
     * When the configuration flag {@link #deleteOnTermination} is {@code true}
     * delete the attached volume before destroy the current running cluster
     *
     * @param event
     */
    @Subscribe
    public void afterClusterTerminate( OnAfterClusterTerminationEvent event ) {

        /*
        * Delete the volume
        */
        if( volumeId && deleteOnTermination ) {
            log.debug "Deleting volume: ${volumeId}  mount on device $device; path: $path "
            TraceHelper.debugTime( "Delete attached volume", { session.getBlockStore().deleteVolume(volumeId) })
        }

    }


    private void makeSnapshot( String volumeId ) {
        log.debug("Creating a snapshot for volume: ${volumeId} as requested by configuration")

        def message = "Blow - Snapshot for volume: ${volumeId}; cluster: ${session.clusterName}"
        def waitFlag = deleteOnTermination  // wait only if we need to delete the 'volume'
        def snapshot = session.blockStore.createSnapshot(volumeId, message, waitFlag)

        log.info("Created snapshot: '${snapshot.getId()}' for volume: '${volumeId}'")

    }


    private List<String> findAttachedVolumeIds() {

        def result = []
        session.listNodes( applyTo ) .each { BlowSession.BlowNodeMetadata node ->

            node.getHardware() .getVolumes() .each { org.jclouds.compute.domain.Volume vol ->

                if( vol.device == device ) {
                    result.add(vol.getId())
                }
            }
        }


        return result

    }

    protected createVolume() {

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
     *  Attach the specified volume/snapshot to the 'master' node
     */
    protected void attachBlockStore() {

        assert volumeId, 'No EBS volumeId available. Something went wrong in the volume creation'
        assert path
        assert device
        assert applyTo

        def node = session.listNodes(applyTo) ?.find()

        log.debug "Attaching volume '$volumeId' to node '${node.getProviderId()}'; path: $path; device: $device; applyTo: $applyTo"
        session.getBlockStore().attachVolume( node.getProviderId(), volumeId, path, device )

    }

    /**
     * Apply the 'master' configuration script
     */
    protected void mountBlockStore() {
        assert applyTo

        session.runScriptOnNodes( scriptMountVolume(), applyTo, true )

    }



    /**
     * @return The BASH script to configure the NSF on the 'master' node
     */
    protected String scriptMountVolume( ) {
        assert userName
        assert path
        assert device

        def final PRE='/dev/sd'
        String altdev = (device ?.startsWith(PRE)) ? '/dev/xvd' + device.substring(PRE.length()) : device

        // the format fragment required for new volumes

        """\
        set -e
        if [ -e '${altdev}' ]; then
          XDEV='${altdev}'
        else
          XDEV='${device}'
        fi

        XPATH=''
        XTYPE=''
        if grep -qs "^\$XDEV" /proc/mounts; then
          XPATH=`grep -P "^\$XDEV" /proc/mounts | cut -f 2 -d ' '`
          XTYPE=`grep -P "^\$XDEV" /proc/mounts | cut -f 3 -d ' '`

          echo "Device \$XDEV already mounted at path \$XPATH -- umount it to remount at ${path}"
          umount -v \$XPATH
        fi

        if [[ ${needFormatVolume} = true && \$XTYPE != ${fsType} ]]; then
          echo "Formatting device \$XDEV using fstype ${fsType}"
          mkfs -t ${fsType} \$XDEV
        fi

        echo "Mounting device \$XDEV to path ${path}"
        [ ! -e ${path} ] && mkdir -p ${path}
        mount -v -t ${fsType} \$XDEV ${path}; sleep 1

        chown -R ${userName}:wheel ${path}
        """
        .stripIndent()

    }


    static private void checkEphemeral( def ephemeralId, String type ) {
        assert ephemeralId != null

        Matcher m = ephemeralId =~ /ephemeral(\d)/

        if( !m.matches() ) {
            throw new BlowConfigException("Not a valid ephemeral identifier: ${ephemeralId}")
        }

        if( !ephemeralAvailByType.containsKey(type) ) {
            log.warn("Unknown ephemeral definition for instance type: '${type}'")
            return
        }

        def num = m.group(1).toInteger()
        def max = ephemeralAvailByType[type]

        if( num >= max ) {
            throw new BlowConfigException("Not a valid ephemeral identifier: ${ephemeralId} for instance type '${type}'")
        }


    }

    /**
     * Verify that the specified snapshot
     * @param snapshotId The snapshot unique identifier
     * @param session The current {@link BlowSession} instance
     * @throws BlowConfigException if the spanshot specified does not exist of is a status different from {@link org.jclouds.ec2.domain.Snapshot.Status#COMPLETED}
     */
    private void checkSnapshot( final String snapshotId ) {
        assert snapshotId

        // method 'findSnapshot' return only the snapshot if it is allocated in the current region
        def snap = session.getBlockStore().findSnapshot(snapshotId)
        if( !snap ) {
            def msg = "Cannot find snapshot '${snapshotId}' in region '${session.conf.regionId}'"
            throw new BlowConfigException(msg)
        }

        log.debug("Snapshot: '${snap.getId()}'; status: '${snap.getStatus()}'; region: '${snap.getRegion()}'")

        if( snap.getStatus() != Snapshot.Status.COMPLETED ) {
            def msg = "Cannot use snapshot '${snapshotId}' because its current status is '${snap.getStatus()}', but it should be '${Snapshot.Status.COMPLETED}'"
            throw new BlowConfigException(msg)
        }

        if( !size ) {
            size = snap.getVolumeSize()
        }
        else if( size < snap.getVolumeSize() ) {
            def msg = "The volume specified size '${size} GB' is too small for the snapshot '${snapshotId}', which requires '${snap.getVolumeSize()} GB'"
            throw new BlowConfigException(msg)
        }

    }

    /**
     * Checks that the specified {@link org.jclouds.ec2.domain.Volume} exists in the current availability zone and is {@link org.jclouds.ec2.domain.Volume.Status#AVAILABLE}
     *
     * @param volumeId The volume unique identifier
     * @param session session The current {@link BlowSession} instance
     * @throws BlowConfigException if the specified volume does not exist or it is allocated
     *   in a wrong availability zone
     */
    private void checkVolume( String volumeId ) {
        assert volumeId

        def vol = session.blockStore.findVolume(volumeId)
        if( !vol ) {
            throw new BlowConfigException("Cannot find volume '${volumeId}' in region '${session.conf.regionId}'")
        }

        log.debug "Volume: ${volumeId}; status: '${vol.getStatus()}'; zone: '${vol.getAvailabilityZone()}'"

        if( vol.getAvailabilityZone() != session.conf.zoneId ) {
            throw new BlowConfigException("Cannot use the volume '${volumeId}' because it is allocated in the availability zone '${vol.getAvailabilityZone()}'. Only volumes in zone '${session.conf.zoneId}' can be used")
        }

        if( vol.getStatus() != Volume.Status.AVAILABLE ) {
            throw new BlowConfigException("Cannot use volume '${volumeId}' because its status is '${vol.getStatus()}', but it should be '${Volume.Status.AVAILABLE}'")
        }
    }

}
