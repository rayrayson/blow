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

import spock.lang.*;

public class NfsOpTest extends Specification {


	public void testMasterTemplate() {
        when:
        def nfs = new NfsOp()
		nfs.path =  "/mydata"
		def tpl = nfs.scriptMaster()

        then:
        tpl.contains("echo \"/mydata	*(rw,async,no_root_squash,no_subtree_check)\" >> /etc/exports")
		tpl.contains("exportfs -ra")


	}


	public void testWorkerTemplate() {

        when:
        def nfs = new NfsOp()
		nfs.masterHostname = "some-home-name"
		nfs.path = "/alpha"
		def tpl = nfs.scriptWorker()

        then:
        tpl.contains('mkdir -p ')
		tpl.contains('echo "some-home-name:/alpha      /alpha      nfs     rw,hard,intr    0 0" >> /etc/fstab')
        tpl.contains('mount -a')
	}


    public void "test validation FAIL"() {
        when:
        new NfsOp().validate()

        then:
        thrown(AssertionError)
    }

    public void "test validation OK"() {
        when:
        new NfsOp( path: "/folder" ).validate()

        then:
        notThrown(AssertionError)
    }
}
