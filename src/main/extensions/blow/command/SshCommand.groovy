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
import org.jclouds.compute.domain.NodeMetadata
import org.jclouds.compute.domain.ExecResponse
import blow.shell.CommandCompletor
import groovy.util.logging.Slf4j
import blow.ssh.SshConsole

/**
 * Run SSH commands on the remote nodes
 *
 * @author Paolo Di Tommaso
 */
@Slf4j
class SshCommand extends AbstractShellCommand implements CommandCompletor {

    /** The  targetHost node on which run the ssh command - or - connect to */
    private String targetHost;

    private String targetCommand

    @Override
    def String getName() { "ssh" }

    /**
     * Parse the list of arguments for the SSH command
     * <p>
     * It follows this syntax:
     * <pre>
     * host [command to execute on the remote host(s)]
     * </pre>
     *
     * @param args
     */
    @Override
    def void parse( def args ) {

        targetHost = args.head()
        targetCommand = args.size()>1 ? args.tail().join(' ') : null

    }


    @Override
    List<String> findOptions(String cmdline) {

        /*
        * Find all available IP addresses
        */
        def result
        if( !cmdline ) {
            result = getSession().listNodes() .collect { NodeMetadata node  ->
                node.getPublicAddresses().find()
            }
        }

        else {
            result = getSession().listNodes() .collect { NodeMetadata node ->
                def entries = []
                def ip = node.getPublicAddresses()?.find();
                if( ip?.startsWith(cmdline) ) entries.add(ip)

                if( node.getProviderId()?.startsWith(cmdline) ) {
                    entries?.add(node.getProviderId())
                }

                if( node.getHostname()?.startsWith(cmdline) ) {
                    entries.add(node.getHostname())
                }

                return entries
            }
            result = result.flatten()
        }

        return result?.sort();
    }

    @Override
    void invoke() {

        if( !targetHost ) {
            println "Provide the node IP address to which connect. "
            return;
        }

        if( !targetCommand ) {
            launchTerm()
            return
        }

        /*
         * Execute the SSH on the remote node
         */

        def session = getSession();
        def filter = session.filterByPublicAddress( makeRegexp(targetHost) )

        def result = session.runScriptOnNodes( targetCommand, filter );
        if( !result ) {
            println "Remote execution terminated with error(s)"
            printResponse(session.errors)
        }
        else {
            printResponse(session.response)
        }
    }

    static void printResponse( Map<String,ExecResponse> response ) {

        int count = 0
        response.each { String node, ExecResponse resp ->
            // ask if he/she want to see all responses
            if( count == 1 ) {
                print "\nPrint all remaining ${response.size()-1} response(s) [y/n]? "
                String answer
                while( (answer=System.console().readLine()) != 'y' && answer != 'n' ) {  }
                println ""
                if( answer == 'n') {
                    return
                };
            }

            // format a string to print out
            StringBuilder out = new StringBuilder();
            if( response.size() > 1 ) {
                out.append("Node '$node' replied:\n")
            }
            if( resp.output ) out.append( resp.output.trim() ) .append("\n")
            if( resp.error ) out.append( resp.error.trim() ) .append("\n")
            print out

            // go on
            count++
        }
    }

    static String makeRegexp( String str ) {
        str = str.replaceAll('\\.', '\\\\.')
        str = str.replaceAll( '\\*', '.*' )
        return str
    }

    private void launchTerm() {

        /*
         * the node can be specified either with the:
         * - IP address
         * - the Hostname
         * - the InstanceID (providerID)
         */
        def node = getSession().listNodes().find { NodeMetadata node ->

                    return node.getPublicAddresses()?.contains(targetHost) \
                        || node.getHostname() == targetHost \
                        || node.getProviderId() == targetHost

        }


        if( !node ) {
            println "There isn'y any running node with the provide name/ip: 'targetHost'"
            return
        }
        // normalize to the IP address
        targetHost = node.getPublicAddresses() ?. find() ?: targetHost

        /*
         * launch the terminal session
         */
        def session = getSession();
        def user = session.conf.userName;
        def key = session.conf.privateKeyFile

//        // find the xterm path
//        def xterm = "which xterm".execute()?.text?.trim();
//        if( !xterm ) {
//            println "Launching SSH requires 'xterm' on your system, but it cannot be found."
//            return;
//        }

//        String[] launch = [xterm, "-e", "ssh -i ${key} ${user}@${targetHost}; read -p 'Press enter to close window'"];
//        Runtime rt = Runtime.getRuntime();
//        Process pr = rt.exec(launch)
//        println "Launching SSH on $targetHost"

        new SshConsole(host: targetHost, user: user, key: key).launch( )
    }

}
