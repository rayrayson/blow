package blow

import blow.plugin.Plugin
import blow.plugin.ConfHolder

/**
 * Created with IntelliJ IDEA.
 * User: ptommaso
 * Date: 4/3/12
 * Time: 5:06 PM
 * To change this template use File | Settings | File Templates.
 */

@Plugin
class TestPlugHolder implements ConfHolder {

    def map = [:]

    @Override
    void setConfProperty(String name, def value) {
        map .put( name, value )
    }
}
