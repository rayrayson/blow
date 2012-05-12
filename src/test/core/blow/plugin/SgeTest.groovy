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

package blow.plugin

import spock.lang.*

class SgeTest extends Specification {



	public void testGetConfString() {
        when:
		def sge = new Sge()
		sge.clusterName = "test"
		sge.nodes = "xxx"
		sge.root = "/opt/sge_root"
		sge.qmasterPort = "1234"
		sge.execdPort = "4321"
		def conf = sge.confTemplate()
		

        then:
		conf.contains("SGE_CLUSTER_NAME=\"test\"")
		conf.contains("SGE_ROOT=\"/opt/sge_root\"")
		conf.contains("SGE_QMASTER_PORT=\"1234\"")
		conf.contains("SGE_EXECD_PORT=\"4321\"")
		conf.contains("CELL_NAME=\"default\"")
		conf.contains("ADMIN_USER=\"\"")
		conf.contains("QMASTER_SPOOL_DIR=\"/opt/sge_root/default/spool/qmaster\"")
		conf.contains("EXECD_SPOOL_DIR=\"/opt/sge_root/default/spool\"")

	}
	
	public void testMasterScript () {

        when:
		def sge = new Sge();
		sge.root = "/some/path"
		sge.cell = "alpha"
		def script = sge.scriptInstallMaster()

        then:
		script .contains( "/some/path/alpha/common/settings.sh" )
	}
	
	public void testScriptDownloadAndCompile() {
		when:
        def sge = new Sge()
		sge.root = "/my-root"
		sge.sourcesTarball = "file.to.download"
		def script = sge.scriptDownloadAndCompile()

        then:
		script .contains( "wget -q \"file.to.download\""  )
		script .contains( "export SGE_ROOT=\"/my-root\"")
	} 


    public void testScriptDowloadBinaries() {
		when:
        def sge = new Sge()
		sge.root = "/my-root"
		sge.binaryZipFile = "install.zip"
		def script = sge.scriptDownloadBinaries()

        then:
		script .contains( "export SGE_ROOT=\"/my-root\"")
		script .contains( "wget -q -O sge6.zip install.zip")
		
	} 

    public void "test validation FAIL "() {

        // missing 'root' installation path
        // exception thrown
        when:
        new Sge(root: "").validation()

        then:
        thrown( AssertionError )

    }

    public void "test validation FAIL 2"() {

        when:
        new Sge(execdPort: "alpha").validation()

        then:
        thrown( AssertionError )

    }

    public void "test validation FAIL 3"() {

        when:
        new Sge(qmasterPort: "alpha").validation()

        then:
        thrown( AssertionError )

    }

    public void "test validation FAIL 4"() {

        when:
        new Sge( cell: null ).validation()

        then:
        thrown( AssertionError )

    }

    public void "test validation FAIL 5"() {
        // missing clusterName, exception thrown
        when:
        new Sge( clusterName: "").validation()

        then:
        thrown( AssertionError )

    }

    public void testValidationOK() {
        when:
        new Sge().validation()

        then:
        notThrown( AssertionError )

    }
	
	
	public void testWorkerScript () {

        when:
		def sge = new Sge();
		sge.root = "/some/path"
		sge.cell = "beta"
		def script = sge.scriptInstallWorker()

        then:
		script .contains( "/some/path/beta/common/settings.sh" )
	}


	public void testScriptSshConf() {

        when:
		def sge = new Sge();
		def script = sge.scriptSshConf()

        then:
		script.contains("ssh-keygen -f ~/.ssh/id_rsa -N ''")
		
	} 
}
