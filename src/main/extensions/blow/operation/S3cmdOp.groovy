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

import blow.events.OnAfterClusterStartedEvent
import blow.util.TraceHelper
import com.google.common.eventbus.Subscribe
import groovy.util.logging.Slf4j
import org.jclouds.scriptbuilder.domain.StatementList
import org.jclouds.scriptbuilder.domain.Statements
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
@Slf4j
@Operation("s3cmd")
class S3cmdOp  {

    @Conf String accessKey
    @Conf String secretKey
    @Conf String encryptionPassword = ""
    @Conf String pathToGPG = ""
    @Conf String useHttps = ""
    @Conf String version = "1.0.1"

    private BlowSession session

    @Validate
    def void validation() {
        assert version, "The 'version' attribute cannot be empty"
    }


    @Subscribe
    public void installS3cmd( OnAfterClusterStartedEvent event ) {
        log.info "Configuring s3cmd"

        TraceHelper.debugTime ("Install S3Cmd") { execute() }

    }

	public void execute() {
        assert version

        if( !accessKey ) accessKey = session.conf.accessKey
        if( !secretKey ) secretKey = session.conf.secretKey

        // copy the s3 configuration file remotely
        def s3lines = [];
        s3conf().eachLine { s3lines.add(it) }
        def s3file = Statements.createOrOverwriteFile("s3conf", s3lines)

        // the export path
        def export = """\
        export PATH="\$PATH:\$HOME/s3cmd-$version"
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
		wget -q http://sourceforge.net/projects/s3tools/files/s3cmd/${version}/s3cmd-${version}.zip/download
        unzip s3cmd-${version}.zip
        chmod +x s3cmd-${version}/s3cmd
        mv s3cmd-${version} \$HOME
        ${export}
        cat s3conf | s3cmd --configure
        rm s3conf
        rm -rf s3cmd-${version}.zip
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
