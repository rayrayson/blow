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
import groovy.util.logging.Slf4j
import lia.util.net.copy.FDT
import org.apache.commons.io.IOUtils
import blow.exception.CommandSyntaxException
import blow.shell.Opt

/**
 *  Fast copy file(s) to/from a remote host.
 *
 *  It is based on the Fast Data Transfer (FDT) protocol - http://monalisa.cern.ch/FDT/
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
class FcopyCommand {


    def BlowSession session


    def nodeFound

    public static final String CMD_DESCRIPTION = ''


    @Cmd(name='fcp', summary='Fast copy file(s) to/from a remote host', usage='fcp [host1:]source-path [host2:]target-path',
         description='''\
fcp copies files between the local file system and a remote host. Source and target paths may contain a host specification to indicate that the file is to be copied to/from that host.

The source path can be a file or a directory specification, if a directory is specified all its content will be copied recursively. The target path MUST specify the target folder that will contain the copied file.

This command uses the Fast Data Transfer (FDT) protocol. Please referer to the following link for information and options details: http://monalisa.cern.ch/FDT/

EXAMPLES
  fcp /user/local/archive.tar.gz 10.1.1.1:/tmp
      Copy file 'archive.tar.gz' to the remote node in the '/tmp' path

  fcp /user/local/tmp/ 10.1.1.1:~
      Copy the local directory 'tmp' (and all its content recursively) to the remote host in the user home

  fcp /user/archive1 /user/archive2 10.1.1.1:~
      Copy the local files 'archive1' and 'archive2' to the remote host in user home

  fcp -P 8 10.1.1.1:/path/to/file1 /folder
      Copy the remote file named 'file1' to the local directory named 'folder', using 8 parallel streams
      ''' )
    def void copy(
            @Opt(opt='bio', description='Blocking I/O mode') Boolean blockingMode,
            @Opt(opt='iof', arg='iof', description='Non-blocking I/O retry factor') Boolean retryFactor,
            @Opt(opt='limit', description='Restrict the transfer speed at the specified rate', arg='rate') def limit,
            @Opt(opt='md5', description='Enables MD5 checksum for every file involved in the transfer') Boolean md5,
            @Opt(opt='printStats', description='Various statistics about buffer pools, sessions, etc will be printed') Boolean printStats,
            @Opt(opt='bs', arg='buffSize', description='Size for the I/O buffers') def bufferSize,
            @Opt(opt='N', description='Disable Nagle algorithm') Boolean disableNagle,
            @Opt(opt='ss', arg='wsz', description='Set the TCP SO_SND_BUFFER size')  def windowSize,
            @Opt(opt='P', arg='noOfStreams', description='Number of paralel streams to use. Default is 4.') def numOfStreams,

            List<String> files ) {

        if( files?.size() < 2 ) {
            throw new CommandSyntaxException('Please specify the source path and the target path of the file(s) to copy')
        }

        nodeFound = false
        for( int i=0; i<files.size(); i++ ) {
            files[i] = resolveHostName(files[i])
        }

        if( !nodeFound ) {
            throw new CommandSyntaxException("Please specify a remote host using the syntax 'host:path'")
        }

        /*
         * Compose the cmd line to invoke the FDT tool
         *
         *
         */
        def cmdline = 'java'
        cmdline += ' -jar ' + fdtJarFile.toString()
        cmdline += ' -p ' + session.conf.fdtPort.toString()
        cmdline += ' -noupdates'
        cmdline += ' -sshKey ' + session.conf.privateKey.absolutePath
        cmdline += ' -remote ' + '\'FDT_JAR="${FDT_HOME:-$HOME}/fdt.jar"; [ ! -e $FDT_JAR ] && wget -q http://s3-eu-west-1.amazonaws.com/cbcrg-eu/fdt.jar -O $FDT_JAR; java -jar $FDT_JAR\''

        // some optional parameter
        if( blockingMode ) cmdline += ' -bio'
        if( retryFactor ) cmdline += "-iof $retryFactor"
        if( limit ) cmdline += " -limit $limit"
        if( md5 ) cmdline += " -md5"
        if( printStats ) cmdline += ' -printStats'
        if( bufferSize ) cmdline += ' -bs ' + bufferSize
        if( disableNagle ) cmdline += ' -N'
        if( windowSize ) cmdline += ' -ss ' + windowSize
        if( numOfStreams ) cmdline += ' -P ' + numOfStreams

        // always use the recursive mode
        cmdline += ' -r'

        // append the list of files to upload
        files.each {
            cmdline += ' ' + it
        }


        log.debug("FDT cmdline: $cmdline" )
        def isWindows = System.properties['os.name']?.startsWith('Windows')
        def builder = isWindows ? new ProcessBuilder('cmd', '/c', 'start', cmdline) : new ProcessBuilder('sh', '-c', cmdline)
        builder.redirectErrorStream(true)

        /**
         * Run it as an external java process
         */
        def proc = builder.start()
        Thread.start('fdt') { System.out << proc.inputStream }
        def result = proc.waitFor()
        log.debug("FDT exitcode: " + result)

    }


    def String resolveHostName( String path ) {

        def pos = path.indexOf(':')
        if( pos == -1 ) {
            // directory names, must end with a wild card symbol
            return new File(path)
        }

        nodeFound = true
        def host = path.substring(0,pos)
        def directory = path.substring(pos+1)

        def node = session.findMatchingNode( host );
        if( !node ) {
            throw new CommandSyntaxException("Unknown remote host name or address: '$host'")
        }


        "${session.conf.userName}@${node.getPublicAddresses()?.find()}:${directory}"

    }

    /** Use the local fdt.jat library or extract it is packed in the distribution package */
    @Lazy
    private File fdtJarFile = {

        URL loc = FDT.class.getProtectionDomain()?.getCodeSource()?.getLocation()

        // if is already available as file on the file system, just us it
        if( loc.toString().startsWith('file:') ) {
            return new File( loc.toString().substring(5) )
        }

        // otherwise we have to extract to a temporary file
        File temp = File.createTempFile('fdt','.jar')
        temp.deleteOnExit()
        OutputStream out = new FileOutputStream(temp)
        IOUtils.copy( loc.openStream(), out )
        out.flush()

        return temp

    } ()
}
