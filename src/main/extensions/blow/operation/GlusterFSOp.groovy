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
import blow.events.OnBeforeClusterStartEvent
import blow.util.PromptHelper
import blow.util.TraceHelper
import com.google.common.eventbus.Subscribe
import groovy.util.logging.Slf4j
import blow.exception.BlowConfigException

/**
 * Manage GlusterFS configuration
 * 
 * @author Paolo Di Tommaso
 *
 */
@Slf4j
@Mixin(PromptHelper)
@Operation("glusterfs")
class GlusterFSOp  {

    private static final RUN_AS_ROOT = true

    /** The list links from where download and install the GlusterFS RMP(s) */
    @Conf
    def rpm = [ "http://download.gluster.org/pub/gluster/glusterfs/3.2/3.2.4/Fedora/glusterfs-core-3.2.4-1.fc11.x86_64.rpm",
                "http://download.gluster.org/pub/gluster/glusterfs/3.2/3.2.4/Fedora/glusterfs-fuse-3.2.4-1.fc11.x86_64.rpm" ]

    /** The GlusterFS volume name */
    @Conf
    String volumeName = "vol1"

	/**
	 * The GlusterFS path to be used to export the mounted volume
	 */
	@Conf def path

    def List<String> bricks = []
	

    // -------------------- private declarations --------------------

    private BlowSession session
	private String serverName
    private String userName


    /**
     * Validate the configuration parameters raising an exception is something is wrong
     */
    @Validate
    public void validate(BlowConfig conf) {
        log.debug "Validating GlusterFS configuration"
        assert path, "The attribute 'path' must be defined in the cluster configuration file"
        assert path.startsWith("/"), "Make sure the 'path' attribute starts with a '/' character"
        assert volumeName, "The attribute 'volume-name' have to be entered in the configuration file"

        if( !serverName ) {
            if( conf.instanceNumFor(conf.masterRole) == 1 ) {
                log.debug("Using '${conf.masterRole}' as Gluster server")
                serverName = conf.masterRole
            }
            else {
                throw new BlowConfigException("GlusterFS require the property 'serverName' to be defined")
            }
        }

        /*
         * check the bricks
         */
        if( !bricks ) {
            bricks = ["$serverName:/gfs/$volumeName"]
        }
        bricks.each {
            assert nodeName(it)
            assert nodePath(it) ?.startsWith("/"), "Gluster brick path for node '${nodeName(it)}' must start with a '/' character"
            assert nodePath(it) != path, "Gluster brick path '${it}' cannot be the same as the mount path: '$path'"
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

        /*
         * configure some runtime operation attributes
         */
        this.userName = session.conf.userName

        /*
         * TODO checks that the bricks names exists in the current definition
         * if requires the list of names to be known before start the cluster
         */
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
         * run the logic tracing the execution time
         */
        TraceHelper.debugTime( "GlusterFS installing components" ) { downloadAndInstall() }
        TraceHelper.debugTime( "GlusterFS configuring master node" ) { configureServer() }
        TraceHelper.debugTime( "GlusterFS configuring worker nodes" ) { configureClients() }
    }



    protected void downloadAndInstall() {
        session.runScriptOnNodes( getInstallScript(), null, RUN_AS_ROOT )
    }


    static nodeName( String value ) {
        def p = value.indexOf(':')
        assert p != -1

        value.substring(0,p)
    }

    static nodePath( String value ) {
        def p = value.indexOf(':')
        assert p != -1

        value.substring(p+1)
    }

    /**
     * Start the daemon and configure the Gluster volume
     * <p>
     * http://download.gluster.com/pub/gluster/glusterfs/3.2/Documentation/AG/html/chap-Administration_Guide-Setting_Volumes.html
     *
     */
	protected void configureServer() {

        /*
         * Create the directory on each brick
         */
        def nodes = []
        bricks.each {
            def node = nodeName(it)
            def dir  = nodePath(it)

            def script = """\
            mkdir -p $dir
            chown -R $userName ${dir}
            """
            .stripIndent()

            // add to the nodes list
            nodes.add(node)

            // run the script
            session.runScriptOnNodes( script, node, true )

        }

        /*
         * The main configuration script
         */
        def script = new StringBuilder()
        script << "service glusterd start \n"

        nodes.remove(serverName)
        nodes.each { script << "gluster peer probe $it \n" }

        // create and start
        script << "gluster volume create ${volumeName} ${bricks.join(' ')} \n"
        script << "gluster volume start ${volumeName} \n"


		session.runScriptOnNodes( script.toString(), serverName, RUN_AS_ROOT )

	}

    /**
     * Configure the Gluster client on each node(s)
     */
	protected void configureClients() {
		session.runScriptOnNodes(getConfClient(), null, RUN_AS_ROOT)
	}


    /**
     * The GlusterFS install script
     * <p>
     * http://download.gluster.com/pub/gluster/glusterfs/3.2/Documentation/IG/html/chap-Installation_Guide-Installing.html
     *
     * @return The BASH script to download and install the GlusterFS RPM
     */
    protected String getInstallScript() {

        /*
         * script fragment to download the RPMs
         */
        def download = ""
        rpm.each {
            download += "wget -q ${it}\n"
        }

        /*
         * Install all of them with the 'rpm' command
         */
        def install = ""
        rpm.each { String item ->
            def name = item.substring(item.lastIndexOf("/")+1)
            install += "rpm -Uvh ${name}\n"
        }

        /*
         * compose the final script
         */
        def result = new StringBuilder()
            .append("# Install required dependencies\n")
            .append("blowpkg -y install fuse fuse-libs\n")
            .append("\n")
            .append("# Download and install the Gluster components\n")
            .append(download)
            .append(install)
            .append("\n")

        return result.toString()
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
        assert volumeName
        assert serverName

        """\
        modprobe fuse

        # Check and create mount path
        [ ! -e ${path} ] && mkdir -p ${path}
        [ -f ${path} ] && echo "The path to be mounted must be a directory. Make sure the following path is NOT a file: '${path}'" && exit 1

        mount -t glusterfs ${serverName}:/${volumeName} ${path}
        """
        .stripIndent()
    }
}
