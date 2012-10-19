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

import spock.lang.Specification
import blow.BlowConfig

class SgeOpTest extends Specification {



	public void testGetConfString() {
        when:
		def sge = new SgeOp()
		sge.clusterName = "test"
		sge.nodes = "xxx"
		sge.path = "/opt/sge_root"
		sge.qmasterPort = "1234"
		sge.execdPort = "4321"
        sge.spool = "/var/sge/spool"
        sge.user = 'illo'
		def conf = sge.confTemplate()
		

        then:
		conf.contains("SGE_CLUSTER_NAME=\"test\"")
		conf.contains("SGE_ROOT=\"/opt/sge_root\"")
		conf.contains("SGE_QMASTER_PORT=\"1234\"")
		conf.contains("SGE_EXECD_PORT=\"4321\"")
		conf.contains("CELL_NAME=\"default\"")
		conf.contains("ADMIN_USER=\"illo\"")
		conf.contains("QMASTER_SPOOL_DIR=\"/var/sge/spool/qmaster\"")
		conf.contains("EXECD_SPOOL_DIR=\"/var/sge/spool\"")
        conf.contains("EXECD_SPOOL_DIR_LOCAL=\"/var/sge/spool/execd\"" )

	}
	
	public void testMasterScript () {

        when:
		def sge = new SgeOp();
		sge.path = '/some/path'
		sge.cell = 'alpha'
        sge.user = 'goofy'
        sge.spool = '/hola'
		def script = sge.scriptInstallMaster()

        then:
        script .contains( '/some/path/alpha/common/settings.sh' )
        script .contains( '[ ! -d /hola ] && sudo mkdir -p /hola && sudo chown -R goofy:wheel /hola' )
	}
	
	public void testScriptDownloadAndCompile() {
		when:
        def sge = new SgeOp()
		sge.path = "/my-root"
		sge.sourcesTarball = "file.to.download"
		def script = sge.scriptDownloadAndCompile()

        then:
		script .contains( "wget -q \"file.to.download\""  )
		script .contains( "export SGE_ROOT=\"/my-root\"")
	} 


    public void testScriptDowloadBinaries() {
		when:
        def sge = new SgeOp()
		sge.path = "/my-root"
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
        new SgeOp(path: "").validation()

        then:
        thrown( AssertionError )

    }

    public void "test validation FAIL 2"() {

        when:
        new SgeOp(execdPort: "alpha").validation()

        then:
        thrown( AssertionError )

    }

    public void "test validation FAIL 3"() {

        when:
        new SgeOp(qmasterPort: "alpha").validation()

        then:
        thrown( AssertionError )

    }

    public void "test validation FAIL 4"() {

        when:
        new SgeOp( cell: null ).validation()

        then:
        thrown( AssertionError )

    }

    public void "test validation FAIL 5"() {
        // missing clusterName, exception thrown
        when:
        new SgeOp( clusterName: "").validation()

        then:
        thrown( AssertionError )

    }

    public void testValidationOK() {
        when:
        new SgeOp().validation(new BlowConfig(instanceNum: 2))

        then:
        notThrown( AssertionError )

    }
	
	
	public void testWorkerScript () {

        when:
		def sge = new SgeOp();
		sge.path = '/some/path'
		sge.cell = 'beta'
        sge.user = 'goofy'
        sge.spool = '/hola'
		def script = sge.scriptInstallWorker()

        then:
		script .contains( '/some/path/beta/common/settings.sh' )
        script .contains( '[ ! -d /hola ] && sudo mkdir -p /hola && sudo chown -R goofy:wheel /hola' )
	}


	public void testScriptSshConf() {

        when:
		def sge = new SgeOp();
		def script = sge.scriptSshConf()

        then:
		script.contains("ssh-keygen -f ~/.ssh/id_rsa -N ''")
		
	} 
}
