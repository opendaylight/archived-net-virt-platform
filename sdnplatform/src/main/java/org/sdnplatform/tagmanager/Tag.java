/*
 * Copyright (c) 2013 Big Switch Networks, Inc.
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the
 * "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.sdnplatform.tagmanager;

/**
 * The interface defines access methods for tag properties.
 * A Tag can be uniquely identified with a tuple, (namespace, name, value).
 * @author kjiang
 *
 */
public class Tag {
    public static final String KEY_SEPARATOR = "|";
    public static final String TAG_ASSIGNMENT = "=";
    public static final String Tag_NS_SEPARATOR = ".";
    
    protected String m_namespace;
    protected String m_name;
    protected String m_value;
    protected boolean persist;
    
    /**
     * Construct a tag from a fully qualified tag string in
     * format: "namespace.name=value", where
     * name is a string without "." and "="
     * @param fullName
     * @param persist
     * @throws TagInvalidNamespaceException 
     * @throws TagInvalidValueException 
     * @throws TagInvalidNameException 
     */
    public Tag(String fullName, boolean persist) 
           throws TagInvalidNamespaceException, 
                  TagInvalidValueException, 
                  TagInvalidNameException {
        if (fullName == null) {
            throw new TagInvalidNamespaceException();
        }
        
        // remove white spaces
        String modName = fullName.replaceAll("\\s", "");
        if (modName.indexOf(TAG_ASSIGNMENT) == -1) {
            throw new TagInvalidValueException();
        }
        
        String[] temp1 = modName.split(TAG_ASSIGNMENT);
        int nsSeparatorIndex = temp1[0].lastIndexOf(Tag_NS_SEPARATOR);
        if (nsSeparatorIndex == temp1[0].length()-1) {
            // "." is the last character of the name.
            throw new TagInvalidNameException();
        } else if (nsSeparatorIndex == -1) {
            // "." is not in the name, so namespace is null.
            this.m_namespace = null;
            this.m_name = temp1[0];
        } else {
            this.m_namespace = temp1[0].substring(0, nsSeparatorIndex);
            this.m_name = temp1[0].substring(nsSeparatorIndex+1);
        }
        
        this.m_value = temp1[1];
        this.persist = persist;
   }
    
    public Tag(String namespace, 
               String name,
               String value,
               boolean persist) {
       m_namespace = namespace;
       m_name = name;
       m_value = value;
       this.persist = persist;
   }
   
    public Tag(String namespace, 
               String name,
               String value) {
       m_namespace = namespace;
       m_name = name;
       m_value = value;
       this.persist = true;
   }
   
    /**
     * Namespace getter
     * @return String
     */
    public String getNamespace() {
        return m_namespace;
    }
    
    /**
     * Namespace setter
     * @param ns
     */
    public void setNamespace(String ns)
        throws TagInvalidNamespaceException {
        if (ns != null && 
                ns.indexOf(KEY_SEPARATOR) != -1) {
            throw new TagInvalidNamespaceException(KEY_SEPARATOR + 
                    " is not allowed in the namespace, " + ns);
        }
        
        m_namespace = ns;
    }
    
    /**
     * Name getter
     * @return String
     */
    public String getName() {
        return m_name;
    }
    
    /**
     * Name setter
     * @param name
     */
    public void setName(String name) 
        throws TagInvalidNameException {
        if (name == null || 
                name.indexOf(KEY_SEPARATOR) != -1) {
            throw new TagInvalidNameException("tag name can't be null or " +
                    KEY_SEPARATOR + " is not allowed in the tag name, " + name);
        }
        m_name = name;
    }
    
    /**
     * value getter
     * @return T
     */
    public String getValue() {
        return m_value;
    }
    
    /**
     * Name setter
     * @param id
     */
    public void setValue(String value)
        throws TagInvalidValueException {
        if (value == null || 
                value.indexOf(KEY_SEPARATOR) != -1) {
            throw new TagInvalidValueException("tag value can't be null or " +
                    KEY_SEPARATOR + " is not allowed in the value, " + value);
        }
        m_value = value;
    }
    
    public String getDBKey() {
        return m_namespace + KEY_SEPARATOR +
            m_name + KEY_SEPARATOR + 
            m_value;
    }
    
    public String getCLIKey() {
        String retStr = "";
        if (m_namespace != null) {
            retStr = m_namespace + ".";
        }
        if (m_name != null) {
            retStr += m_name + "=";
        }
        if (m_value != null) {
            retStr += m_value;
        }
        return retStr;
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof Tag))
            return false;
        Tag other = (Tag) obj;
        if (!m_namespace.equals(other.m_namespace))
            return false;
        if (!m_name.equals(other.m_name))
            return false;
        if (!m_value.equals(other.m_value))
            return false;
        return true;
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     * 
     * The two tags are the same if they are in the same namespace and have the same id.
     */
    
    
    
    @Override
    public int hashCode() {
        final int prime = 7867;
        int result;
        result = m_namespace.hashCode();
        result = prime * result + m_name.hashCode();
        result = prime * result + m_value.hashCode();
        return result;
    }
    
    @Override
    public String toString() {
        return "Tag NS:" + m_namespace  + " name:" + m_name +
                " value:" + m_value;
    }

    public boolean getPersist() {
        return persist;
    }
}
