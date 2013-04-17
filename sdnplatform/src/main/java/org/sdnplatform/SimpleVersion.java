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

package org.sdnplatform;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 
 * A very simple class that can parse and compare software version strings. 
 * The current assumptions are: 
 *   - Version number is of the form x.y.z 
 *   - x, y, and z are all integers
 *   - x == major, y == minor, z == build parts of the version number
 *   - searches for the version string anywhere in a string. doesn't require
 *     spaces around the version. 
 * @author gregor
 *
 */
public class SimpleVersion implements Comparable<SimpleVersion> {
    static final protected Pattern versionPattern =
            Pattern.compile("(\\d+).(\\d+).(\\d+)");
    
    protected int major;
    protected int minor;
    protected int build;
    
    /**
     * Create a new SimpleVersion object
     * Searches for a valid version string in the given string and parses 
     * it. 
     * @param version
     * @throws IllegalArgumentException if the version pattern isn't found
     * or if the version doesn't parse correctly.
     */
    public SimpleVersion(String version) throws IllegalArgumentException {
        setVersion(version);
    }
    
    /**
     * Create a  SimpleVersion object with all components set to 0
     */
    public SimpleVersion() {
        clear();
    }
    
    /**
     * Clear the version. Set all components to 0
     */
    public void clear() {
        major = minor = build = 0;
    }
    
    /**
     * Searches for a valid version string in the given string and parses 
     * it. 
     * @param version
     * @throws IllegalArgumentException if the version pattern isn't found
     * or if the version doesn't parse correctly.
     */
    public void setVersion(String version) throws IllegalArgumentException {
        Matcher versionMatcher = 
                versionPattern.matcher(version);
        if (versionMatcher.find() && versionMatcher.groupCount() == 3) {
            try {
                major = Integer.parseInt(versionMatcher.group(1));
                minor = Integer.parseInt(versionMatcher.group(2));
                build = Integer.parseInt(versionMatcher.group(3));
            } catch (NumberFormatException e) {
                clear();
                throw new IllegalArgumentException("Could not parse version " +
                        versionMatcher.group(0));
            }
        }
        else {
            clear();
            throw new IllegalArgumentException("No version string found in " +
                    "input string");
        }
    }
    
    

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getBuild() {
        return build;
    }
    
    

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + build;
        result = prime * result + major;
        result = prime * result + minor;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        SimpleVersion other = (SimpleVersion) obj;
        if (build != other.build) return false;
        if (major != other.major) return false;
        if (minor != other.minor) return false;
        return true;
    }

    @Override
    public int compareTo(SimpleVersion arg0) {
        if (major < arg0.major) 
            return -1;
        else if (major > arg0.major)
            return 1;
        if (minor < arg0.minor) 
            return -1;
        else if (minor > arg0.minor)
            return 1;
        if (build < arg0.build) 
            return -1;
        else if (build > arg0.build)
            return 1;
        else 
            return 0;
    }
}
