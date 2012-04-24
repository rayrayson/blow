package blow

import blow.plugin.Plugin
import blow.plugin.ConfHolder

/**
 * @author Paolo Di Tommaso
 */

@Plugin
class TestPlugHolder implements ConfHolder {

    def map = [:]

    @Override
    void setConfProperty(String name, def value) {
        map .put( name, value )
    }
}
