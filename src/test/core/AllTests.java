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

import blow.BlowConfigTest;
import blow.BlowSessionTest;
import blow.DynLoaderFactoryTest;
import blow.DynLoaderTest;
import blow.command.SshCommandTest;
import blow.operation.*;
import blow.shell.BlowShellTest;
import blow.ssh.ScpClientTest;
import blow.util.CmdLineHelperTest;
import blow.util.KeyPairBuilderTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import util.TypesafeConfigTest;


/**
 * All tests 
 * 
 * <p>
 * Give a try to this as an alternative to load all tests from the classpath
 * 
 * http://johanneslink.net/projects/cpsuite.jsp
 * 
 * @author Paolo Di Tommaso
 *
 */

@RunWith(Suite.class)
@SuiteClasses({
    BlowSessionTest.class,
	BlowConfigTest.class,
	DynLoaderTest.class,
	DynLoaderTest.class,
    DynLoaderFactoryTest.class,
    BlowShellTest.class,
    CmdLineHelperTest.class,
    KeyPairBuilderTest.class,

    SshCommandTest.class,

    AppendTextTest.class,
	NfsTest.class,
	SgeOpTest.class,
    AwsCredetentialsOpTest.class,
    PilotHelpTest.class,

	OperationFactoryTest.class,
	ScpClientTest.class,

	TypesafeConfigTest.class

})
public class AllTests {

}
