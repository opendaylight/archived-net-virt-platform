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

package org.sdnplatform.devicegroup;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.sdnplatform.devicemanager.IDevice;
import org.sdnplatform.tagmanager.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * Match against the tag rules
 * @author readams
 *
 */
public class TagMatcher<T extends IDeviceGroup>
extends AbstractRuleMatcher<T> {
    protected static Logger logger = 
            LoggerFactory.getLogger(TagMatcher.class);
    
    public TagMatcher(DeviceGroupMatcher<T> deviceGroupMatcher) {
        super(deviceGroupMatcher);
    }

    @Override
    public String getName() {
        return "tag";
    }

    @Override
    public Set<MembershipRule<T>> match(IDevice d) {
        if (deviceGroupMatcher.tagRuleMap.size() == 0) return null;
        
        Set<MembershipRule<T>> matchingRules = new TreeSet<MembershipRule<T>>();
        
            Set<Tag> deviceTags = deviceGroupMatcher.tagManager.getTagsByDevice(d);
            if (deviceTags == null) return null;
            
            HashMap<MembershipRule<T>, Set<String>> matchMap = 
                new HashMap<MembershipRule<T>, Set<String>>();
            for (Tag tag : deviceTags) {
                String tagKey = tag.getDBKey();
                Set<MembershipRule<T>> curTagMatchingRules = 
                        deviceGroupMatcher.tagRuleMap.get(tagKey);
                if (curTagMatchingRules == null) continue;
                for (MembershipRule<T> rule: curTagMatchingRules) {
                    Set<String> matchedTags = matchMap.get(rule);
                    if (matchedTags == null) {
                        matchedTags = new HashSet<String>();
                        matchMap.put(rule, matchedTags);
                    }
                    matchedTags.add(tagKey);
                }
            }
            
            Iterator<Map.Entry<MembershipRule<T>, Set<String>>> it = 
                matchMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<MembershipRule<T>, Set<String>> entry = it.next();
                Set<String> ruleTags = entry.getValue();
                MembershipRule<T> rule = entry.getKey();
                if (ruleTags != null && 
                        ruleTags.size() == rule.getTagList().size()) {
                    matchingRules.add(rule);
                }
            }
        if (matchingRules.size() == 0) return null;
        return matchingRules;
    }

    @Override
    public boolean ruleHasField(MembershipRule<T> rule) {
        return (rule.getTags() != null);
    }

}
