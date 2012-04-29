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

package blow.command

import blow.shell.AbstractShellCommand
import blow.shell.CommandCompletor
import blow.BlowSession
import blow.shell.BlowShell
import blow.shell.Cmd
import blow.shell.Synopsis
import org.jclouds.compute.domain.NodeMetadata
import blow.shell.Completion

/**
 * Print out information details for the specified node
 *
 * @author Paolo Di Tommaso
 */
class NodesInfoCommand  {

    def BlowSession session

    @Cmd
    @Synopsis("List the nodes available in the current running cluster")
    def listnodes() {

        def nodes = session.listNodes() ;
        if( nodes ) {
            nodes.each { NodeMetadata node ->
                println "${node.providerId}; ${node.getPublicAddresses()?.find()?.padLeft(15)}; ${node.getHostname()?.padLeft(15)}; ${node.state}; ${node.group}; ${node.getUserMetadata()?.'Role'}"
            }
            return
        }

        println "(no nodes available)"
    }


    @Cmd
    @Synopsis("Shows the information details for the specified node")
    @Completion({ cmdline -> session.findMatchingAttributes( cmdline, "providerId" ) })

	def nodeinfo ( String nodeId ) {

        if( !nodeId ) {  
            println "(please spoecify the node-id for which show the info)"
            return
        }

        def node = session.findMatchingNode( nodeId )
        if( !node ) {
            println "(cannot find any information for node: '$nodeId')"
            return
        }

		session.printNodeInfo( node.getProviderId() )
	}



    @Cmd
    @Synopsis("Shows the information details of the 'master' node")
    public void masterinfo() {
        def nodeId = session.getMasterMetadata()?.getProviderId()
        if( nodeId ) {
            session.printNodeInfo( nodeId )
        }
        else {
            println "(no master node info available)"
        }
    }


    @Cmd
    @Synopsis("Shows the 'hostname' of the running nodes in current cluster")
    public void listhostnames() {
        session.listHostNames().each {
            println it
        }
    }


}