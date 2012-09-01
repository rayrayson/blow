package blow

import blow.operation.Conf
import blow.operation.Operation
import blow.operation.Validate

@Operation("my-super-operation")
class TestOperation extends TestOperationBase {

	@Conf def value1;

	@Conf
	String value2;

    BlowSession session

    @Validate
    def void validate() {

        println "OK"

    }

	
}
