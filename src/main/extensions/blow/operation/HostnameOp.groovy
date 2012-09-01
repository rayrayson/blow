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
 * Configure the <code>/etc/hostname</code> file in each node in the cluster
 * 
 * @author Paolo Di Tommaso
 *
 */
@Slf4j
@Operation("hostname")
class HostnameOp  {


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
       yum install -y wget
       HOSTIP=`wget -q -O - http://169.254.169.254/latest/meta-data/local-ipv4`
       HOSTNAME=`cat /etc/hosts | grep \$HOSTIP | cut -f 2`
       hostname \$HOSTNAME
       """
       .stripIndent()


       def statementsToRun = Statements.newStatementList(
               Statements.appendFile("/etc/hosts", hostnameList),
               Statements.exec(setHostname) )

       session.runStatementOnNodes(statementsToRun, null, true)


	}

	
}
