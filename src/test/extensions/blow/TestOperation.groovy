package blow

import blow.operation.Conf

import blow.operation.Validate
import blow.operation.Operation;



@Operation("my-super-operation")
class TestOperation extends TestOperationBase {

	@Conf def value1;

	@Conf("value-2") 
	String value2;


    @Validate
    def void validate() {

        println "OK"

    }

	
}
