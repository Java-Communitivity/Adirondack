/**
 * $RCSfile$
 * $Revision: 2579 $
 * $Date: 2005-02-08 16:48:59 -0500 (Tue, 08 Feb 2005) $
 *
 * Copyright 2005 Jive Software.
 *
 * All rights reserved. Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.xmpp.component;

/**
 * Factory to get a ComponentManager implementation. The ComponentManager implementation
 * used will determined in the following way:<ul>
 *
 *      <li>An external process can set the ComponentManager using
 *      {@link #setComponentManager(ComponentManager)}.
 *      <li>If the component manager is <tt>null</tt>, the factory will check for
 *      the Java system property "whack.componentManagerClass". The value of the
 *      property should be the fully qualified class name of a ComponentManager
 *      implementation (e.g. com.foo.MyComponentManager). The class must have a default
 *      constructor.
 * </ul>
 *
 * @author Matt Tucker
 */
public class ComponentManagerFactory {

    private static ComponentManager componentManager;

    /**
     * Returns a ComponentManager instance.
     *
     * @return a ComponentManager instance.
     */
    public static synchronized ComponentManager getComponentManager() {
        if (componentManager != null) {
            return componentManager;
        }
        // ComponentManager is null so we have to try to figure out how to load
        // an instance. Look for a Java property.
        String className = System.getProperty("whack.componentManagerClass");
        if (className != null) {
            try {
                Class c = Class.forName(className);
                componentManager = (ComponentManager)c.newInstance();
                return componentManager;
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        // Got here, so throw exception.
        throw new NullPointerException("No ComponentManager implementation available.");
    }

    /**
     * Sets the ComponentManager instance that will be used.
     *
     * @param manager the ComponentManager instance.
     */
    public static void setComponentManager(ComponentManager manager) {
        componentManager = manager;
    }
}
