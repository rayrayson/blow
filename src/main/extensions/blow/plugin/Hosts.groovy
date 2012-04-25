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

import com.google.common.eventbus.Subscribe
import groovy.util.logging.Slf4j
import org.jclouds.compute.domain.NodeMetadata
import org.jclouds.compute.options.TemplateOptions
import org.jclouds.scriptbuilder.domain.AppendFile

import blow.events.OnAfterClusterCreateEvent
import blow.util.TraceHelper
import blow.BlowSession

/**
 * Configure the <code>/etc/hosts</code> file in each node in the cluster 
 * 
 * @author Paolo Di Tommaso
 *
 */
@Slf4j
@Plugin("hosts")
class Hosts {

	
	@Subscribe
	public void configureHostsFile( OnAfterClusterCreateEvent event ) {
        log.info "Configuring hosts file"

        TraceHelper.debugTime( "Configure '/etc/hosts' file") {
            configureHostsTask(event.session)
        }
	}
	
	/**
	 * Creates the /etc/hosts files containing a reference for all the nodes in the cluster
	 * and upload it to the remote nodes
	 * 
	 * @param session The {@link BlowSession} instance
	 * 
	 */
	protected void configureHostsTask( BlowSession session ) {
		
	   // get all the nodes (private) IPs and host names
	   List<String> hosts = []
	   session.listNodes().each { NodeMetadata node ->
		   hosts.add( String.format("%s\t%s", node.getPrivateAddresses().find(), node.getHostname()) )
	   }
			   
	   // uploaded to all nodes
	   AppendFile appender = new AppendFile( "/etc/hosts", hosts)

	   def credential = session.conf.credentials
	   def opt = TemplateOptions.Builder.overrideLoginCredentials(credential).runAsRoot(true)
	   def response = session.compute.runScriptOnNodesMatching(session.filterAll(), appender, opt)
	   
	   response.each {
	   
		   def msg = " ${it.key.getHostname()} -> "
		   
		   if( it.value.getExitCode() ) {
			   // there is an error
			   msg += "${it.value.getExitCode()} - ${it.value.getError()} - ${it.value.getOutput()}"
		   }
		   else {
			   msg +=  "OK"
		   }

           blow.plugin.Hosts.log.debug msg
	   }
		
	}

	
}
