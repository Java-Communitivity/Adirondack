/**
 * $RCSfile$
 * $Revision: 5623 $
 * $Date: 2006-10-06 02:28:55 -0400 (Fri, 06 Oct 2006) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.util;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

import java.io.*;
import java.util.*;

/**
 * Provides the the ability to use simple XML property files. Each property is
 * in the form X.Y.Z, which would map to an XML snippet of:
 * <pre>
 * &lt;X&gt;
 *     &lt;Y&gt;
 *         &lt;Z&gt;someValue&lt;/Z&gt;
 *     &lt;/Y&gt;
 * &lt;/X&gt;
 * </pre>
 * <p/>
 * The XML file is passed in to the constructor and must be readable and
 * writtable. Setting property values will automatically persist those value
 * to disk. The file encoding used is UTF-8.
 *
 * @author Derek DeMoro
 * @author Iain Shigeoka
 */
public class XMLProperties {

    private File file;
    private Document document;

    /**
     * Parsing the XML file every time we need a property is slow. Therefore,
     * we use a Map to cache property values that are accessed more than once.
     */
    private Map<String, String> propertyCache = new HashMap<String, String>();

    /**
     * Creates a new XMLPropertiesTest object.
     *
     * @param fileName the full path the file that properties should be read from
     *                 and written to.
     * @throws IOException if an error occurs loading the properties.
     */
    public XMLProperties(String fileName) throws IOException {
        this(new File(fileName));
    }

    /**
     * Loads XML properties from a stream.
     *
     * @param in the input stream of XML.
     * @throws IOException if an exception occurs when reading the stream.
     */
    public XMLProperties(InputStream in) throws IOException {
        Reader reader = new BufferedReader(new InputStreamReader(in));
        buildDoc(reader);
    }

    /**
     * Creates a new XMLPropertiesTest object.
     *
     * @param file the file that properties should be read from and written to.
     * @throws IOException if an error occurs loading the properties.
     */
    public XMLProperties(File file) throws IOException {
        this.file = file;
        if (!file.exists()) {
            // Attempt to recover from this error case by seeing if the
            // tmp file exists. It's possible that the rename of the
            // tmp file failed the last time Jive was running,
            // but that it exists now.
            File tempFile;
            tempFile = new File(file.getParentFile(), file.getName() + ".tmp");
            if (tempFile.exists()) {
                System.err.println("WARNING: " + file.getName() + " was not found, but temp file from " +
                        "previous write operation was. Attempting automatic recovery." +
                        " Please check file for data consistency.");
                tempFile.renameTo(file);
            }
            // There isn't a possible way to recover from the file not
            // being there, so throw an error.
            else {
                throw new FileNotFoundException("XML properties file does not exist: "
                        + file.getName());
            }
        }
        // Check read and write privs.
        if (!file.canRead()) {
            throw new IOException("XML properties file must be readable: " + file.getName());
        }
        if (!file.canWrite()) {
            throw new IOException("XML properties file must be writable: " + file.getName());
        }

        FileReader reader = new FileReader(file);
        buildDoc(reader);
    }

    /**
     * Returns the value of the specified property.
     *
     * @param name the name of the property to get.
     * @return the value of the specified property.
     */
    public synchronized String getProperty(String name) {
        String value = propertyCache.get(name);
        if (value != null) {
            return value;
        }

        String[] propName = parsePropertyName(name);
        // Search for this property by traversing down the XML heirarchy.
        Element element = document.getRootElement();
        for (int i = 0; i < propName.length; i++) {
            element = element.element(propName[i]);
            if (element == null) {
                // This node doesn't match this part of the property name which
                // indicates this property doesn't exist so return null.
                return null;
            }
        }
        // At this point, we found a matching property, so return its value.
        // Empty strings are returned as null.
        value = element.getTextTrim();
        if ("".equals(value)) {
            return null;
        }
        else {
            // Add to cache so that getting property next time is fast.
            propertyCache.put(name, value);
            return value;
        }
    }

    /**
     * Return all values who's path matches the given property
     * name as a String array, or an empty array if the if there
     * are no children. This allows you to retrieve several values
     * with the same property name. For example, consider the
     * XML file entry:
     * <pre>
     * &lt;foo&gt;
     *     &lt;bar&gt;
     *         &lt;prop&gt;some value&lt;/prop&gt;
     *         &lt;prop&gt;other value&lt;/prop&gt;
     *         &lt;prop&gt;last value&lt;/prop&gt;
     *     &lt;/bar&gt;
     * &lt;/foo&gt;
     * </pre>
     * If you call getProperties("foo.bar.prop") will return a string array containing
     * {"some value", "other value", "last value"}.
     *
     * @param name the name of the property to retrieve
     * @return all child property values for the given node name.
     */
    public String[] getProperties(String name) {
        String[] propName = parsePropertyName(name);
        // Search for this property by traversing down the XML heirarchy,
        // stopping one short.
        Element element = document.getRootElement();
        for (int i = 0; i < propName.length - 1; i++) {
            element = element.element(propName[i]);
            if (element == null) {
                // This node doesn't match this part of the property name which
                // indicates this property doesn't exist so return empty array.
                return new String[]{};
            }
        }
        // We found matching property, return names of children.
        Iterator iter = element.elementIterator(propName[propName.length - 1]);
        List<String> props = new ArrayList<String>();
        String value;
        while (iter.hasNext()) {
            // Empty strings are skipped.
            value = ((Element)iter.next()).getTextTrim();
            if (!"".equals(value)) {
                props.add(value);
            }
        }
        String[] childrenNames = new String[props.size()];
        return props.toArray(childrenNames);
    }

    /**
     * Return all values who's path matches the given property
     * name as a String array, or an empty array if the if there
     * are no children. This allows you to retrieve several values
     * with the same property name. For example, consider the
     * XML file entry:
     * <pre>
     * &lt;foo&gt;
     *     &lt;bar&gt;
     *         &lt;prop&gt;some value&lt;/prop&gt;
     *         &lt;prop&gt;other value&lt;/prop&gt;
     *         &lt;prop&gt;last value&lt;/prop&gt;
     *     &lt;/bar&gt;
     * &lt;/foo&gt;
     * </pre>
     * If you call getProperties("foo.bar.prop") will return a string array containing
     * {"some value", "other value", "last value"}.
     *
     * @param name the name of the property to retrieve
     * @return all child property values for the given node name.
     */
    public Iterator getChildProperties(String name) {
        String[] propName = parsePropertyName(name);
        // Search for this property by traversing down the XML heirarchy,
        // stopping one short.
        Element element = document.getRootElement();
        for (int i = 0; i < propName.length - 1; i++) {
            element = element.element(propName[i]);
            if (element == null) {
                // This node doesn't match this part of the property name which
                // indicates this property doesn't exist so return empty array.
                return Collections.EMPTY_LIST.iterator();
            }
        }
        // We found matching property, return values of the children.
        Iterator iter = element.elementIterator(propName[propName.length - 1]);
        ArrayList<String> props = new ArrayList<String>();
        while (iter.hasNext()) {
            props.add(((Element)iter.next()).getText());
        }
        return props.iterator();
    }

    /**
     * Returns the value of the attribute of the given property name or <tt>null</tt>
     * if it doesn't exist. Note, this
     *
     * @param name the property name to lookup - ie, "foo.bar"
     * @param attribute the name of the attribute, ie "id"
     * @return the value of the attribute of the given property or <tt>null</tt> if
     *      it doesn't exist.
     */
    public String getAttribute(String name, String attribute) {
        if (name == null || attribute == null) {
            return null;
        }
        String[] propName = parsePropertyName(name);
        // Search for this property by traversing down the XML heirarchy.
        Element element = document.getRootElement();
        for (int i = 0; i < propName.length; i++) {
            String child = propName[i];
            element = element.element(child);
            if (element == null) {
                // This node doesn't match this part of the property name which
                // indicates this property doesn't exist so return empty array.
                break;
            }
        }
        if (element != null) {
            // Get its attribute values
            return element.attributeValue(attribute);
        }
        return null;
    }

    /**
     * Sets a property to an array of values. Multiple values matching the same property
     * is mapped to an XML file as multiple elements containing each value.
     * For example, using the name "foo.bar.prop", and the value string array containing
     * {"some value", "other value", "last value"} would produce the following XML:
     * <pre>
     * &lt;foo&gt;
     *     &lt;bar&gt;
     *         &lt;prop&gt;some value&lt;/prop&gt;
     *         &lt;prop&gt;other value&lt;/prop&gt;
     *         &lt;prop&gt;last value&lt;/prop&gt;
     *     &lt;/bar&gt;
     * &lt;/foo&gt;
     * </pre>
     *
     * @param name the name of the property.
     * @param values the values for the property (can be empty but not null).
     */
    public void setProperties(String name, List<String> values) {
        String[] propName = parsePropertyName(name);
        // Search for this property by traversing down the XML heirarchy,
        // stopping one short.
        Element element = document.getRootElement();
        for (int i = 0; i < propName.length - 1; i++) {
            // If we don't find this part of the property in the XML heirarchy
            // we add it as a new node
            if (element.element(propName[i]) == null) {
                element.addElement(propName[i]);
            }
            element = element.element(propName[i]);
        }
        String childName = propName[propName.length - 1];
        // We found matching property, clear all children.
        List toRemove = new ArrayList();
        Iterator iter = element.elementIterator(childName);
        while (iter.hasNext()) {
            toRemove.add(iter.next());
        }
        for (iter = toRemove.iterator(); iter.hasNext();) {
            element.remove((Element)iter.next());
        }
        // Add the new children.
        for (String value : values) {
            Element childElement = element.addElement(childName);
            if (value.startsWith("<![CDATA[")) {
                childElement.addCDATA(value.substring(9, value.length()-3));
            }
            else {
                childElement.setText(value);
            }
        }
        saveProperties();

        // Generate event.
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("value", values);
        PropertyEventDispatcher.dispatchEvent(name,
                PropertyEventDispatcher.EventType.xml_property_set, params);
    }

    /**
     * Return all children property names of a parent property as a String array,
     * or an empty array if the if there are no children. For example, given
     * the properties <tt>X.Y.A</tt>, <tt>X.Y.B</tt>, and <tt>X.Y.C</tt>, then
     * the child properties of <tt>X.Y</tt> are <tt>A</tt>, <tt>B</tt>, and
     * <tt>C</tt>.
     *
     * @param parent the name of the parent property.
     * @return all child property values for the given parent.
     */
    public String[] getChildrenProperties(String parent) {
        String[] propName = parsePropertyName(parent);
        // Search for this property by traversing down the XML heirarchy.
        Element element = document.getRootElement();
        for (int i = 0; i < propName.length; i++) {
            element = element.element(propName[i]);
            if (element == null) {
                // This node doesn't match this part of the property name which
                // indicates this property doesn't exist so return empty array.
                return new String[]{};
            }
        }
        // We found matching property, return names of children.
        List children = element.elements();
        int childCount = children.size();
        String[] childrenNames = new String[childCount];
        for (int i = 0; i < childCount; i++) {
            childrenNames[i] = ((Element)children.get(i)).getName();
        }
        return childrenNames;
    }

    /**
     * Sets the value of the specified property. If the property doesn't
     * currently exist, it will be automatically created.
     *
     * @param name  the name of the property to set.
     * @param value the new value for the property.
     */
    public synchronized void setProperty(String name, String value) {
        if (name == null) {
            return;
        }
        if (value == null) {
            value = "";
        }

        // Set cache correctly with prop name and value.
        propertyCache.put(name, value);

        String[] propName = parsePropertyName(name);
        // Search for this property by traversing down the XML heirarchy.
        Element element = document.getRootElement();
        for (int i = 0; i < propName.length; i++) {
            // If we don't find this part of the property in the XML heirarchy
            // we add it as a new node
            if (element.element(propName[i]) == null) {
                element.addElement(propName[i]);
            }
            element = element.element(propName[i]);
        }
        // Set the value of the property in this node.
        if (value.startsWith("<![CDATA[")) {
            element.addCDATA(value.substring(9, value.length()-3));
        }
        else {
            element.setText(value);
        }
        // Write the XML properties to disk
        saveProperties();

        // Generate event.
        Map<String, String> params = new HashMap<String,String>();
        params.put("value", value);
        PropertyEventDispatcher.dispatchEvent(name,
                PropertyEventDispatcher.EventType.xml_property_set, params);
    }

    /**
     * Deletes the specified property.
     *
     * @param name the property to delete.
     */
    public synchronized void deleteProperty(String name) {
        // Remove property from cache.
        propertyCache.remove(name);

        String[] propName = parsePropertyName(name);
        // Search for this property by traversing down the XML heirarchy.
        Element element = document.getRootElement();
        for (int i = 0; i < propName.length - 1; i++) {
            element = element.element(propName[i]);
            // Can't find the property so return.
            if (element == null) {
                return;
            }
        }
        // Found the correct element to remove, so remove it...
        element.remove(element.element(propName[propName.length - 1]));
        // .. then write to disk.
        saveProperties();

        // Generate event.
        PropertyEventDispatcher.dispatchEvent(name,
                PropertyEventDispatcher.EventType.xml_property_deleted, Collections.emptyMap());
    }

    /**
     * Builds the document XML model up based the given reader of XML data.
     */
    private void buildDoc(Reader in) throws IOException {
        try {
            SAXReader xmlReader = new SAXReader();
            document = xmlReader.read(in);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new IOException(e.getMessage());
        }
        finally {
            if (in != null) {
                in.close();
            }
        }
    }

    /**
     * Saves the properties to disk as an XML document. A temporary file is
     * used during the writing process for maximum safety.
     */
    private synchronized void saveProperties() {
        boolean error = false;
        // Write data out to a temporary file first.
        File tempFile = null;
        Writer writer = null;
        try {
            tempFile = new File(file.getParentFile(), file.getName() + ".tmp");
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tempFile)));
            OutputFormat prettyPrinter = OutputFormat.createPrettyPrint();
            XMLWriter xmlWriter = new XMLWriter(writer, prettyPrinter);
            xmlWriter.write(document);
        }
        catch (Exception e) {
            e.printStackTrace();
            // There were errors so abort replacing the old property file.
            error = true;
        }
        finally {
            if (writer != null) {
                try {
                    writer.close();
                }
                catch (IOException e1) {
                    e1.printStackTrace();
                    error = true;
                }
            }
        }

        // No errors occured, so delete the main file.
        if (!error) {
            // Delete the old file so we can replace it.
            if (!file.delete()) {
                System.err.println("Error deleting property file: " + file.getAbsolutePath());
                return;
            }
            // Copy new contents to the file.
            try {
                copy(tempFile, file);
            }
            catch (Exception e) {
                e.printStackTrace();
                // There were errors so abort replacing the old property file.
                error = true;
            }
            // If no errors, delete the temp file.
            if (!error) {
                tempFile.delete();
            }
        }
    }

    /**
     * Returns an array representation of the given Jive property. Jive
     * properties are always in the format "prop.name.is.this" which would be
     * represented as an array of four Strings.
     *
     * @param name the name of the Jive property.
     * @return an array representation of the given Jive property.
     */
    private String[] parsePropertyName(String name) {
        List<String> propName = new ArrayList<String>(5);
        // Use a StringTokenizer to tokenize the property name.
        StringTokenizer tokenizer = new StringTokenizer(name, ".");
        while (tokenizer.hasMoreTokens()) {
            propName.add(tokenizer.nextToken());
        }
        return propName.toArray(new String[propName.size()]);
    }

    public void setProperties(Map<String, String> propertyMap) {
        for (String propertyName : propertyMap.keySet()) {
            String propertyValue = propertyMap.get(propertyName);
            setProperty(propertyName, propertyValue);
        }
    }

    /**
     * Copies the inFile to the outFile.
     *
     * @param inFile  The file to copy from
     * @param outFile The file to copy to
     * @throws IOException If there was a problem making the copy
     */
    private static void copy(File inFile, File outFile) throws IOException {
        FileInputStream fin = null;
        FileOutputStream fout = null;
        try {
            fin = new FileInputStream(inFile);
            fout = new FileOutputStream(outFile);
            copy(fin, fout);
        }
        finally {
            try {
                if (fin != null) fin.close();
            }
            catch (IOException e) {
                // do nothing
            }
            try {
                if (fout != null) fout.close();
            }
            catch (IOException e) {
                // do nothing
            }
        }
    }

    /**
     * Copies data from an input stream to an output stream
     *
     * @param in the stream to copy data from.
     * @param out the stream to copy data to.
     * @throws IOException if there's trouble during the copy.
     */
    private static void copy(InputStream in, OutputStream out) throws IOException {
        // Do not allow other threads to intrude on streams during copy.
        synchronized (in) {
            synchronized (out) {
                byte[] buffer = new byte[256];
                while (true) {
                    int bytesRead = in.read(buffer);
                    if (bytesRead == -1) break;
                    out.write(buffer, 0, bytesRead);
                }
            }
        }
    }
}
