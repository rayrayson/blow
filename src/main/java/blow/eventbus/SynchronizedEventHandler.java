package blow.eventbus;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Wraps a single-argument 'handler' method on a specific object, and ensures
 * that only one thread may enter the method at a time.
 *
 * <p>Beyond synchronization, this class behaves identically to
 * {@link EventHandler}.
 *
 * @author Cliff Biffle
 */
class SynchronizedEventHandler extends EventHandler {
  /**
   * Creates a new SynchronizedEventHandler to wrap {@code method} on
   * {@code target}.
   *
   * @param target  object to which the method applies.
   * @param method  handler method.
   */
  public SynchronizedEventHandler(Object target, Method method) {
    super(target, method);
  }

  @Override public synchronized void handleEvent(Object event)
      throws InvocationTargetException {
    super.handleEvent(event);
  }

}