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
import blow.util.TraceHelper
import com.google.common.eventbus.Subscribe
import groovy.util.logging.Slf4j
import org.jclouds.scriptbuilder.domain.Statements

/**
 * Default operation executed on any node. It does
 * <ul>
 * <li>Install 'wget'</li>
 * <li>Configure host names</li>
 * <ul>
 *
 * 
 * @author Paolo Di Tommaso
 *
 */
@Slf4j
@Operation
class DefaultOp {


    /*
     * The current session inject by Blow
     */
    private BlowSession session


	@Subscribe
	public void configureHostsFile( OnAfterClusterStartedEvent event ) {
        log.info "Configuring cluster hostname(s)"

        TraceHelper.debugTime( "Configure '/etc/hosts' file") {
            configureHostsTask()
        }
	}
	
	/**
	 * Creates the /etc/hosts files containing a reference for all the nodes in the cluster
	 * and upload it to the remote nodes
	 * 
	 * @param session The {@link BlowSession} instance
	 * 
	 */
	protected void configureHostsTask( ) {

        /*
         * Create a link named 'blow_pkg' to the platform package manager
         * (zypper, yum, apt-get) so all the following script can refers
         * to it independently the target platform
         */
        String createPackageManagerAlias = '''\
        
        if command -v zypper &>/dev/null; then
            FILENAME=`which zypper`
        elif command -v yum &>/dev/null; then
            FILENAME=`which yum`
        elif command -v apt-get &>/dev/null; then
            FILENAME=`which apt-get`
        else
            FILENAME=''
        fi

        if [ -z $FILENAME ]; then
            echo 'Cannot find any package manager'
        else
            FOLDER=`dirname $FILENAME`; cd $FOLDER
            ln -s "$FILENAME" blowpkg
            cd -
        fi

        '''
        .stripIndent()

	    /*
	     * Make a list containing each node a pair (IP address - Node name)
	     * to be appended to the node 'hosts' file
	     */
	    List<String> hostnameList = []
	    session.listNodes().each { BlowSession.BlowNodeMetadata node ->
		   hostnameList.add( String.format("%s\t%s", node.getPrivateAddresses().find(), node.getNodeName()) )
	    }
			   

        /*
         * This script using the node public
         */
        def setHostname = """\
        blowpkg install -y wget unzip
        HOSTIP=`wget -q -O - http://169.254.169.254/latest/meta-data/local-ipv4`
        HOSTNAME=`cat /etc/hosts | grep -F \$HOSTIP | cut -f 2`
        hostname \$HOSTNAME
        """
        .stripIndent()


        /*
         * Disable the firewall (Fedora 16)
         */
        def disableFirewall = """\
        if command -v systemctl &>/dev/null; then
            systemctl stop iptables.service
            systemctl stop ip6tables.service
            systemctl disable iptables.service
            systemctl disable ip6tables.service
        else
            service iptables stop
            service ip6tables stop
            chkconfig iptables off
            chkconfig ip6tables off
        fi
        """
        .stripIndent()

        def disableSELinux = """\
        # disable selinux
        if command -v setenforce &>/dev/null; then
            setenforce 0
        fi
        """
        .stripIndent()

        def statementsToRun = Statements.newStatementList(
               Statements.exec(createPackageManagerAlias),
               Statements.appendFile("/etc/hosts", hostnameList),
               Statements.exec(setHostname),
               Statements.exec(disableFirewall),
               Statements.exec(disableSELinux),
               Statements.exec(disableStrictHostChecking()),
               Statements.exec(installPrivateKey())
        )

        session.runStatementOnNodes(statementsToRun, null, true)


	}

    /**
     * Disable the SSH strict host checking
     *
     * @return
     */
    private String disableStrictHostChecking() {

        def user = session.conf.userName
        def config = "~$user/.ssh/config"

        """\
        echo "Host *" >> ~/.ssh/config
        echo "  StrictHostKeyChecking no" >> $config
        echo "  UserKnownHostsFile /dev/null" >> $config
        chmod 600 $config
        chown $user $config
        """
         .stripIndent()
    }



    /**
     * Install the private key, this is make it possible to
     * access slaves node via SSH without password (required by some components like SGE and Hadoop)
     *
     */
    private String installPrivateKey() {

        def user = session.conf.userName
        def keyFile =  session.conf.privateKey.text.contains('DSA PRIVATE KEY') ? 'id_dsa' : 'id_rsa'
        keyFile = "~$user/.ssh/" + keyFile

        def lines = []
        lines << "cat > ${keyFile} << 'EOF'"
        lines << session.conf.privateKey.text
        lines << "EOF"
        lines << "chmod 600 ${keyFile}"
        lines << "chown $user ${keyFile}"

        return lines.join("\n")

    }



}
