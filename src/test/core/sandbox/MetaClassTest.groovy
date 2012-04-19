package sandbox;

import static org.junit.Assert.*;

import org.junit.Test;

public class MetaClassTest {

	@Test
	public void test() {
		MetaClass meta = Hola.getMetaClass()
		assert meta  != null
		
		MetaMethod method = meta.getMetaMethod("sayHola", String)
		assert null != method
		
		println method.getSignature()
		
	}

}

class Hola {
	
	def sayHola( String hello ) {
		
		println hello
		
	}
	
}