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
import blow.events.OnAfterClusterStartedEvent
import blow.events.OnAfterClusterTerminationEvent
import blow.events.OnBeforeClusterStartEvent
import blow.events.OnBeforeClusterTerminationEvent
import blow.events.OnBeforeNodeLaunchEvent
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
@Operation("nfs")
class NfsOp  {

    /**
     * The NFS path to be used to export the mounted volume
     */
    @Conf def path

    @Conf def export

    /**
     *  Defines the storage property for the attached volume
     */
    @Conf def supply

    /**
     * Linux device name to be used to mount the volume
     */
    @Conf def device

    /**
     * Specify the new size when mounting a new EBS volume
     */
    @Conf Integer size

    @Conf
    def boolean deleteOnTermination

    @Conf
    def boolean makeSnapshotOnTermination


    /* ---------- private declarations ------------- */

    /**
     * The current session object
     */
    private BlowSession session

    /**
     * Delegate volume operation
     */
    private EbsVolumeOp volume

    private String userName

    private String master

    private String worker


    /**
     * Validate the configuration parameters raising an exception is something is wrong
     */
    @Validate
    public void validate(BlowConfig config) {
        log.debug "Validating NFS configuration"
        assert path, "You need to define the 'path' attribute in the NFS configuration"
        assert path.startsWith("/"), "The volume path value must begin with a '/' character: '${path}'"

        /*
        * Make sure that the 'roles' defined matches the component 'topology'
        */
        assert config.instanceNumFor(config.masterRole) == 1, "The NFS op requires the '${config.masterRole}' role to declare exactly one node"
        assert config.instanceNumFor(config.workersRole) >= 1, "The NFS op requires the '${config.workersRole}' role to declare at least one node"

        /*
         * Validate the EBS volume
         */
        if( supply ) {
            volume = new EbsVolumeOp()
            volume.device = this.device
            volume.supply = this.supply != "new" ? this.supply : null // <-- note: for new volume
            volume.path = this.path
            volume.size = this.size
            volume.deleteOnTermination = this.deleteOnTermination
            volume.makeSnapshotOnTermination = this.makeSnapshotOnTermination
            volume.quiet = true  // <-- print less info when embedding in this operation
            volume.applyTo = config.masterRole

            // validate the volume
            volume.validate(config)
        }


        /*
         * Keep track of the defined user name
         */
        userName = config.userName
        master = config.masterRole
        worker = config.workersRole

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
    public void beforeClusterStart( OnBeforeClusterStartEvent event ) {
        /*
         * Initialize and check the volume
         */
        if( volume ) {
            volume.session = this.session
            volume.beforeClusterStart(event)
        }
    }

    @Subscribe
    def void beforeNodeLaunch( OnBeforeNodeLaunchEvent event ) {
        if( volume ) {
            volume.beforeNodeLaunch(event)
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
    public void afterClusterStarted( OnAfterClusterStartedEvent event ) {
        log.info "Configuring NFS file system for shared path '${path}'"

        /* mount the volume if required */
        if( volume ) {
            volume.afterClusterStarted(event)
        }


        /*
         * run the logic tracing the execution time
         */
        TraceHelper.debugTime( "NFS configuring '${master}' node", { configureMaster() }  )
        TraceHelper.debugTime( "NFS configuring '${worker}' node(s)", { configureWorkers() } )
    }


    @Subscribe
    public void beforeClusterTerminate( OnBeforeClusterTerminationEvent event ) {
        if( volume ) {
            volume.beforeClusterTerminate(event)
        }
    }

    @Subscribe
    public void afterClusterTerminate( OnAfterClusterTerminationEvent event ) {
        if( volume ) {
            volume.afterClusterTerminate(event)
        }
    }


    /**
     * Apply the 'master' configuration script
     */
    protected void configureMaster() {

        def script = scriptMaster()

        // prepend the script fragment for path creation
        if( !volume ) {
            script = makePath() + script
        }

        session.runScriptOnNodes( script, master, true )

    }

    /**
     * Apply the client configuration script to each 'worker'
     */
    protected void configureWorkers() {

        if( session.conf.size < 2 ) { /* nothing to do */ return }
        session.runScriptOnNodes(scriptWorker(), worker, true)
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
        chown -R ${userName} ${path}
        """
        .stripIndent()
    }

    /**
     * TODO
     * Fedora 16 has changed
     * http://raman-kumar.blogspot.com.es/2011/12/nfs-setup-in-fedora16.html
     *
     * @return The BASH script to configure the NSF on the 'master' node
     */
    protected String scriptMaster( ) {
        assert path

        """\
        # Installing nfs components
        blowpkg install -y nfs-utils rpcbind

        #
        # Exporting the shared FS
        #
        echo "${path}	*(rw,async,no_root_squash,no_subtree_check)" >> /etc/exports
        echo "rpcbind : ALL : allow" >> /etc/hosts.allow
        echo "portmap : AL: : allow" >> /etc/hosts.allow

        #
        # Configuring services
        #
        if command -v systemctl &>/dev/null; then
          systemctl start rpcbind.service
          systemctl start nfs-server.service
          systemctl start nfs-lock.service
        else
          service rpcbind start
          service nfslock start
          service nfs start
          service nfs restart
          chkconfig --level 2345 rpcbind on
          chkconfig --level 2345 nfslock on
          chkconfig --level 2345 nfs on
        fi

        """
        .stripIndent()
    }

    /**
     * @return The NFS configuration script to be executed on the 'worker' nodes
     */
    protected String scriptWorker() {
        assert path
        assert master


        """\
        # Installing nfs components
        blowpkg install -y nfs-utils rpcbind

        #
        # Configuring services
        #
        if command -v systemctl &>/dev/null; then
          systemctl start rpcbind.service
          systemctl start nfs-lock.service
        else
          service rpcbind start
          service nfslock start
          chkconfig --level 2345 rpcbind on
          chkconfig --level 2345 nfslock on
        fi

        #
        # Create mount point and mount it
        #
        mkdir -p ${path}
        echo "${master}:${path}      ${path}      nfs     rw,hard,intr    0 0" >> /etc/fstab
        mount -av
        """
        .stripIndent()

    }
}
