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
import blow.BlowSession

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class AwsCredetentialsOpTest extends Specification {


    def testScript() {

        when:
        def aws = new AwsCredentialsOp()
        aws.session = new BlowSession()
        aws.session.conf.accessKey = '1234'
        aws.session.conf.secretKey = 's3cret'

        then:
        aws.script() .contains( 'export AWS_ACCESS_KEY=1234' )
        aws.script() .contains( 'export AWS_SECRET_KEY=s3cret' )
        aws.script() .contains( 'AWSAccessKeyId=1234' )
        aws.script() .contains( 'AWSSecretKey=s3cret' )

    }

}
