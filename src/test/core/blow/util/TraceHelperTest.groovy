package blow.util

import org.junit.Test

/**
 * @author Paolo Di Tommaso
 */
class TraceHelperTest  {

    @Test
    public void run() {

        TraceHelper.debugTime("Hola") {

            println "Ciao"

        }

    }


}
