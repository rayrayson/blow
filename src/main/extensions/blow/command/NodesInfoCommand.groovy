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

package blow.command

import blow.BlowSession
import blow.shell.Cmd
import blow.shell.Completion
import blow.util.PromptHelper
import blow.shell.CmdParams
import com.beust.jcommander.Parameter

/**
 * Print out information details for the specified node
 *
 * @author Paolo Di Tommaso
 */
@Mixin(PromptHelper)
class NodesInfoCommand  {

    def BlowSession session

    /**
     * Parameter class for the 'listnodes' command
     */
    static class ListNodesParams extends CmdParams {
        @Parameter( names='-d', description='Show node details' )
        Boolean showDetails
    }

    /**
     * List the nodes that made-up the cluster
     *
     * @param params
     */
    @Cmd(name='listnodes', summary="List the nodes available in the current running cluster")
    def void listNodes( ListNodesParams params ) {



//        def nodes = session.listNodes() ?.sort { BlowSession.BlowNodeMetadata node1, BlowSession.BlowNodeMetadata node2 ->
//
//            // sort by role
//            def r1 = session.conf.roles.indexOf( node1.getNodeRole() )
//            def r2 = session.conf.roles.indexOf( node2.getNodeRole() )
//            def comp = r1 <=> r2
//            // if are in the same role, sort by the name
//            if( comp == 0 ) {
//                comp = node1.getNodeName() <=> node2.getNodeName()
//            }
//
//            return comp
//        }

        /*
         * first pass, just to get some number for nice formatting
         */
        def lName = 0
        def lType = 0
        def lIp = 0
        def lImage = 0
        def lRam = 0
        session.listNodes().each { BlowSession.BlowNodeMetadata node ->

            // keep track of the longest node name
            if( node.getNodeName()?.length()>lName ) {
                lName = node.getNodeName().length()
            }

            if( node.getNodeIp() ?.length() > lIp ) {
                lIp =  node.getNodeIp() .length()
            }

            if( node.getImageId()?.length() > lImage ) {
                lImage = node.getImageId().length()
            }

            def ram = node.getHardware() ?.getRam() ?.toString()
            if( ram ?.length() > lRam ) {
                lRam = ram.length()
            }

            if( node.getHardware()?.getId()?.length() > lType ) {
                lType = node.getHardware().getId().length()
            }

        }


        if( !session.allNodes ) {
            println "(no nodes available)"
            return
        }


        session.allNodes.each { String name, BlowSession.BlowNodeMetadata node ->

            // the node name
            print "${name?.padRight(lName)}"

            if( node == null ) {
                println "  --"
                return
            }

            /*
             * print node info
             */
            def row = " (${node.providerId}); "

            row += node.getNodeIp() ? "${node.getNodeIp()?.padRight(lIp)}" : "--".padRight(lIp)
            row += "; "
            row += "${node.state}"

            if( params.showDetails == true ) {

                row += "; " +
                        "${node.getImageId().padRight(lImage)}; " +
                        "${node.getHardware()?.getId().padRight(lType)}; " +
                        "${node.getHardware()?.getProcessors()?.size()} cpu - " +
                        "${node.getHardware()?.getRam().toString().padLeft(lRam)} MB"
            }


            println row

        }

    }

    /**
     * Shows the detailed information of the spcified node
     *
     * @param nodeId
     */
    @Cmd(name='nodeinfo', summary="Shows the information details for the specified node")
    @Completion({ cmdline -> session.findMatchingAttributes(cmdline) })

	def void nodeInfo ( String nodeId ) {

        if( !nodeId ) {  
            println "(please specify the node-id for which show the info)"
            return
        }

        def node = session.findMatchingNode( nodeId )
        if( !node ) {
            println "(cannot find any information for node: '$nodeId')"
            return
        }

		println session.getNodeInfoString( node.getId() )
	}


    @Cmd(summary='Refresh the cluster nodes metadata')
    public void refresh() {
        print 'refreshing ...'
        session.refreshMetadata()
        print '\r'
        listNodes( new ListNodesParams() )
    }

    @Cmd(summary='Save the current session data', usage='saveSession [file name]')
    public void saveSession(String fileName) {

        def file
        if( fileName ) {
            file = new File(fileName)
            if( file.exists() ) {
                def answer = promptYesOrNo("The file '$file' already exist. Do you want to overwrite it?")
                if( answer != 'y' ) {
                    return
                }
            }

            file = session.persist(file)
        }
        else {
            file = session.persist()
        }

        println "Session saved to file '${file}'"

    }


}
