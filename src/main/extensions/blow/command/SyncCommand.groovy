/*
 * Copyright (c) 2012. Paolo Di Tommaso.
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
import blow.util.CmdLineHelper
import groovy.util.logging.Slf4j
import groovyx.gpars.GParsPool
import jsr166y.ForkJoinPool
import org.apache.commons.io.FilenameUtils
import blow.shell.*
import org.jclouds.compute.domain.NodeMetadata

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
class SyncCommand {

    def BlowShell shell
    def BlowSession session


    @Cmd
    @Synopsis("Syncronize a local file/folder with a single or multiple target nodes")
    @Completion( { fileNameCompletion(it) } )
    def void sync (
            String source,
            String target,
            @Opt(longOpt='delete', description='delete extraneous files from destination dirs') def delete,
            @Opt(argName='r', longOpt='recursive', description='recurse into directories') def recursive,
            @Opt(argName='l', longOpt='links', description='copy symlinks as symlinks') def copyLinks,
            @Opt(argName='a', longOpt='archive', description='archive mode') def archive
            )
    {

        def sourceFile = new File(source)
        if( !sourceFile.exists() ) {
            println "The source path: '$source' does not exist."
            return
        }

        if( !target || !target.contains(':')) {
            println "You have to specify the remote node"
            return
        }

        def p = target.indexOf(":")
        def targetNode = target.substring(0,p)
        def targetPath = target.substring(p+1)

        log.debug "targetNode: $targetNode "
        log.debug "targetPath: $targetPath "

        if( !targetNode ) {
            println "You have to specify the remote node"
            return
        }


        GParsPool.withPool(10) { ForkJoinPool pool ->

            def nodes
            if( "#master" == targetNode ) {
                nodes = session.getMasterMetadata()
            }
            else if( "#worker" == targetNode ) {
                nodes = session.listByRole('worker')
            }
            else if( "*" == targetNode ) {
                nodes = session.listNodes()
            }
            else {
                nodes = session.findMatchingNode(targetNode)
            }


            if( nodes == null ) {
                println "Cannot find any matching node for your rule: $targetNode"
                return
            }


            nodes.eachParallel { NodeMetadata node ->

                def cmd =  [
                            "rsync",
                            "-arvlp",
                            "-e",
                            "ssh -i ${session.conf.privateKeyFile}",
                            "--delete",
                            sourceFile,
                            "${node.getPublicAddresses().find()}:${targetPath}"
                ]

                log.debug "sync command: " + cmd

                def proc = new ProcessBuilder(cmd).start()
                proc.waitFor()


            }


            println "Sync complete"
            pool.awaitTermination()
        }



    }

    /**
     * Give the command line as entered by the user returns a list of valid options to
     * complete the command line
     *
     * @param cmdLine The CLI string entered by the user after the 'sync' command itself
     */
    private fileNameCompletion( def cmdLine ) {

        def path = CmdLineHelper.getFileTokens( cmdLine ?: "" )

        /*
         * returns all the files the contained in that folder that starts with specified prefix
         */
        def result = new LinkedList<String>()
        log.debug "Looking in folder '${path.parentPath}' for files starting with: '${path.name}'"

        path.parentFile.eachFile { File file ->
            if( file.name.startsWith( path.name ) ) {
                def resultPath = FilenameUtils.concat( path.parentPath, file.getName())
                def resultFile = new File(resultPath)

                result.add( resultPath )

                if( resultFile.exists() && resultFile.isDirectory() ) {
                    resultFile.eachFile { result.add( FilenameUtils.concat(resultPath, it.getName()) )  }
                }
            }
        }

        if( result.size()>100 ) {
            print "\nDisplay all ${result.size()} possibilities? (y or n) "
            def ch
            while( (ch = System.in.read()) != -1 )  {
                if( ch == 'y' as char ) break
                else if( ch == 'n' as char ) { println(); return [] }
            }
       }

        return result
    }



}
