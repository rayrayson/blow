package blow

import blow.plugin.Conf

import blow.plugin.Plugin
import blow.plugin.Validate;



@Plugin("my-super-plugin")
class TestPlugin extends TestPluginBase {

	@Conf def value1;

	@Conf("value-2") 
	String value2;


    @Validate
    def void validate() {

        println "OK"

    }

	
}
