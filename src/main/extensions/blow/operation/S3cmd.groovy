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

import org.jclouds.scriptbuilder.domain.CreateFile
import org.jclouds.scriptbuilder.domain.StatementList

import org.jclouds.scriptbuilder.domain.Statements
import groovy.util.logging.Slf4j
import com.google.common.eventbus.Subscribe

import blow.util.TraceHelper
import blow.events.OnAfterClusterStartedEvent
import blow.BlowSession

/**
 * Install and configure 's3cmd' cmd line tools 
 * <p>
 * See http://s3tools.org/s3cmd
 * 
 * 
 * @author Paolo Di Tommaso
 *
 */
@Operation("s3cmd")
@Slf4j
class S3cmd {

    @Conf String accessKey
    @Conf String secretKey
    @Conf String encryptionPassword = ""
    @Conf String pathToGPG = ""
    @Conf String useHttps = ""


    @Subscribe
    public void installS3cmd( OnAfterClusterStartedEvent event ) {


        TraceHelper.debugTime ( "Install S3Cmd", {

            execute( event.session )

        } )

    }

	public void execute(BlowSession session) {

        blow.operation.S3cmd.log.debug "Running s3cmd"
        if( !accessKey ) accessKey = session.conf.accessKey
        if( !secretKey ) secretKey = session.conf.secretKey

        // copy the s3 configuration file remotely
        def s3lines = [];
        s3conf().eachLine { s3lines.add(it) }
        def s3file = new CreateFile("s3conf", s3lines);

        // the export path
        def export = """\
        export PATH="\$PATH:\$HOME/s3cmd-1.1.0-beta3"
        """
        .stripIndent()

        /*
         * The installation script
         * - download the zipped packaged in the current directory (a tmp subfolder)
         * - unzip it
         * - move the extracted package to the HOME directory
         * - add the bin folder to the PATH
         * - run the 's3cmd' configuration procedure, which requires some data interactively (so they are provided on the stdin)
         * - delete useless stuff
         */
		String script = """\
        sudo yum install -y wget
		wget http://sourceforge.net/projects/s3tools/files/s3cmd/1.1.0-beta3/s3cmd-1.1.0-beta3.zip/download
        unzip s3cmd-1.1.0-beta3.zip
        mv s3cmd-1.1.0-beta3 \$HOME
        ${export}
        cat s3conf | s3cmd --configure
        rm s3conf
        rm s3cmd-1.1.0-beta3.zip
		"""
        .stripIndent()

        /*
         * Running all the process
         * 1) create the configuration file
         * 2) run the install script
         * 3) append the install folder to the PATH in the '.bash_profile'
         */
        def recipe = new StatementList(
                s3file,
                Statements.exec(script),
                Statements.appendFile( "~/.bash_profile", [ export ] )
        )

        session.runStatementOnNodes(recipe)
	}

    /**
     * The s3cmd tools needs to be configured using the --configure cmd line option
     * <p>
     * It asks for some data interactively, so to automate the process that data
     * is provided with the below method via pipe redirection on the stdin
     * @return
     */
    String s3conf() {
        """\
        AKIAIF3TUNLSJYG5UWQQ
        ejMHuOyLJUiZQpkgxf4srt8WcpFwj3PDL9o8RMKz
        ${encryptionPassword}
        ${pathToGPG}
        ${useHttps}

        n
        y
        """
        .stripIndent()

    }

}
