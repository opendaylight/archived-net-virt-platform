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

package org.sdnplatform.doc;

import ch.qos.logback.classic.Level;

/**
 * Thin wrapper class for JSON serialization/deserialization
 * @author readams
 *
 */
public class LogMessageDocItemData implements Comparable<LogMessageDocItemData> {
    public String category;
    public String severity;
    public String message;
    public String explanation;
    public String recommendation;
    public String className;

    public LogMessageDocItemData() {
    }

    public String getCategory() {
        return category;
    }

    public String getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    @Override
    public int compareTo(LogMessageDocItemData o) {
        int v = getCategory().compareTo(o.getCategory());
        if (v != 0) return v;
        try {
            Level l1 = Level.valueOf(getSeverity());
            Level l2 = Level.valueOf(o.getSeverity());
            v = l1.toInteger().compareTo(l2.toInteger());
            if (v != 0) return v;
        } catch (Exception e) {}
        v = getSeverity().compareTo(o.getSeverity());
        if (v != 0) return v;
        v = getMessage().compareTo(o.getMessage());
        if (v != 0) return v;
        return v;
    }
}