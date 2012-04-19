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

import spock.lang.*;

public class NfsTest extends Specification {


	public void testMasterTemplate() {
        when:
        def nfs = new Nfs()
        nfs.masterHostname = "xxx"
		nfs.device = "/dev/abc"
		nfs.volumeId = "vol-999"
		nfs.path =  "/mydata"
		nfs.userName = "illo"
		def tpl = nfs.scriptMaster(true)

        then:
        tpl.contains("echo \"/mydata	*(rw,async,no_root_squash,no_subtree_check)\" >> /etc/exports")
		tpl.contains("chown -R illo:wheel /mydata")
		tpl.contains( "mount /dev/abc /mydata;" )


	}

    public void testMasterTemplate2 () {

        when:
        def nfs = new Nfs()
        nfs.masterHostname = "xxx"
        nfs.device = "/dev/abc"
        nfs.volumeId = "vol-999"
        nfs.path =  "/mydata"
        nfs.userName = "illo"
        def tpl = nfs.scriptMaster(false)

        then:
        !tpl.contains( "mount /dev/abc /mydata" )

    }


	public void testWorkerTemplate() {

        when:
        def nfs = new Nfs()
		nfs.masterHostname = "xxx"
		nfs.device = "/dev/abc"
		nfs.volumeId = "vol-999"
		nfs.path = "/alpha"
		def tpl = nfs.scriptWorker()

        then:
		tpl.contains("echo \"xxx:/alpha      /alpha      nfs     rw,hard,intr    0 0\" >> /etc/fstab")
	}


    public void "test validation FAIL"() {
        when:
        new Nfs().validate()

        then:
        thrown(AssertionError)
    }

    public void "test validation OK"() {
        when:
        new Nfs( path: "/folder" ).validate()

        then:
        notThrown(AssertionError)
    }
}
