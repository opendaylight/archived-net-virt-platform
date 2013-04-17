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

package org.sdnplatform.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * A trie for efficiently finding entries that are prefixes of the query string.
 * Note this trie does not support concurrent modification, and attempts to do
 * so will result in undefined behavior.
 * 
 * @author readams
 *
 * @param <V> The value stored in the trie
 */
public class IPV4SubnetTrie<V> {
    private class Link {
        public Link(TrieEntry entry) {
            super();
            this.entry = entry;
        }
        TrieEntry entry;
        
        int label;
        int bits;
    }
    
    /**
     * Implement an entry for our trie
     * @author readams
     */
    protected class TrieEntry implements Map.Entry<IPV4Subnet, V> {
        IPV4Subnet key;
        V value;
        
        Link left;
        Link right;
        
        public TrieEntry(IPV4Subnet key, V value) {
            super();
            this.key = key;
            this.value = value;
        }
        
        public boolean isEmpty() {
            return (value == null);
        }
        
        // ************************
        // Map.Entry<IPV4Subnet, V>
        // ************************

        @Override
        public IPV4Subnet getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(V value) {
            throw new UnsupportedOperationException();
        }
        
    }
    
    protected Link root = null;
    int size = 0;

    // **************
    // Public methods
    // **************

    /**
     * Empty out this trie
     */
    public void clear() {
        root = null;
        size = 0;
    }
    
    /**
     * Get the number of elements in the trie
     * @return
     */
    public int size() {
        return size;
    }
    
    /**
     * Add a value to the trie
     * @param key the key to add
     * @param value the value to add
     * @return the old value associated with this key, if it existed
     */
    public V put(IPV4Subnet key, V value) {
        Link current = root;
        int keylength = lengthInBits(key);
        
        if (root == null) {
            root = new Link(new TrieEntry(key, value));
            root.bits = keylength;
            root.label = key.address;
            root.label &= (~0 << (Integer.SIZE - root.bits));
            size += 1;
            return null;
        }
        
        int index = 0;
        int totalindex = 0;
        
        while (true) {
            while (index < current.bits) {
                boolean cb = isBitSet(current.label, current.bits, index);

                if ((totalindex + index) >= keylength) {
                    // more label remaining on current link but key has ended
                    Link newl = new Link(new TrieEntry(current.entry.key, 
                                                       current.entry.value));

                    newl.bits = current.bits - index;
                    newl.label = current.label << index;
                    newl.label &= (~0 << (Integer.SIZE - newl.bits));
                    newl.entry.left = current.entry.left;
                    newl.entry.right = current.entry.right;

                    current.entry.key = key;
                    current.entry.value = value;
                    current.bits = index;
                    current.label &= (~0 << (Integer.SIZE - current.bits));                    
                    
                    if (cb) {
                        current.entry.left = newl;
                        current.entry.right = null;
                    } else {
                        current.entry.left = null;
                        current.entry.right = newl;
                    }
                    size += 1;
                    return null;
                }
                
                boolean kb = isBitSet(key, totalindex + index);
                if (cb != kb) {
                    // more remaining on both current label and key.
                    // need to split label here
                    Link newl = new Link(new TrieEntry(key, value));
                    Link oldl = new Link(current.entry);

                    // newl label is the remaining bits in the key 
                    newl.bits = keylength - totalindex - index;
                    newl.label = key.address << (totalindex + index);
                    newl.label &= (~0 << (Integer.SIZE - newl.bits));
                    
                    // oldl label is current label shifted by index
                    oldl.bits = current.bits - index;
                    oldl.label = current.label << index;

                    // truncate label on current
                    current.bits = index;
                    current.label &= (~0 << (Integer.SIZE - current.bits));
                    
                    // set up new intermediate node
                    current.entry = new TrieEntry(null, null);                    
                    if (kb) {
                        // new link will be left tree
                        current.entry.left = newl;
                        current.entry.right = oldl;
                    } else {
                        // new link will be right tree
                        current.entry.left = oldl;
                        current.entry.right = newl;                        
                    }
                    size += 1;
                    return null;
                }
                index += 1;
            }
            // label is a match, traverse branches
            if ((totalindex + index) < keylength) {
                if (isBitSet(key, totalindex + index)) {
                    // left branch
                    if (current.entry.left == null) {
                        current.entry.left = new Link(null);
                    }
                    current = current.entry.left;
                } else {
                    // right branch
                    if (current.entry.right == null) {
                        current.entry.right = new Link(null);
                    }
                    current = current.entry.right;
                }
                if (current.entry == null) {
                    current.entry = new TrieEntry(key, value);
                    // label is remaining bits in the key
                    current.bits = keylength - totalindex - index;
                    current.label = key.address << (totalindex + index);
                    current.label &= (~0 << (Integer.SIZE - current.bits));
                    size += 1;
                    return null;
                }
                totalindex += index;
                index = 0;
            } else {
                // exact match for existing leaf
                V oldv = current.entry.value;
                current.entry.key = key;
                current.entry.value = value;
                return oldv;
            }
        }
    }

    /**
     * Retrieve a specific value from the Trie
     * @param key
     * @return
     */
    public V get(IPV4Subnet key) {
        Link current = root;
        int keylength = lengthInBits(key);
        int index = 0;
        int totalindex = 0;
        while (current != null) {
            if ((keylength - totalindex) < current.bits)
                return null;
            while (index < current.bits) {
                boolean cb = isBitSet(current.label, current.bits, index);
                boolean kb = isBitSet(key, totalindex + index);
                if (cb != kb) {
                    return null;
                }
                index += 1;
            }
            if ((totalindex + index) < keylength) {
                if (isBitSet(key, totalindex + index)) {
                    current = current.entry.left;
                } else {
                    current = current.entry.right;
                }
            } else if ((totalindex + index) == keylength) {
                return current.entry.getValue();
            } else {
                return null;
            }
            totalindex += index;
            index = 0;
        }
        return null;
    }
    
    /**
     * Final all entries in the trie that are prefixes for the given entry
     * @param key
     * @return a list of entries, possibly null
     */
    public List<Entry<IPV4Subnet,V>> prefixSearch(IPV4Subnet key) {
        List<Entry<IPV4Subnet,V>> resultSet = null;
        
        Link current = root;
        int keylength = lengthInBits(key);
        int index = 0;
        int totalindex = 0;
        while (current != null) {
            if ((keylength - totalindex) < current.bits)
                return resultSet;
            while (index < current.bits) {
                boolean cb = isBitSet(current.label, current.bits, index);
                boolean kb = isBitSet(key, totalindex + index);
                if (cb != kb) {
                    return resultSet;
                }
                index += 1;
            }
            if ((totalindex + index) <= keylength) {
                if (current.entry.value != null) {
                    if (resultSet == null)
                        resultSet = new ArrayList<Entry<IPV4Subnet,V>>();
                    resultSet.add(current.entry);
                }
                if (isBitSet(key, totalindex + index)) {
                    current = current.entry.left;
                } else {
                    current = current.entry.right;
                }
            } else {
                return resultSet;
            }
            totalindex += index;
            index = 0;
        }
        return resultSet;
    }
    
    // ***************
    // Private methods
    // ***************

    protected static boolean isBitSet(IPV4Subnet key, int bitIndex) {
        return isBitSet(key.address, key.maskBits, bitIndex);
    }

    protected static boolean isBitSet(int key, int maskBits, int bitIndex) {
        int masked = key & ((1 << Integer.SIZE-1) >>> bitIndex);
        return ((bitIndex < maskBits) && masked != 0);
    }

    protected static boolean isPrefix(IPV4Subnet key, IPV4Subnet prefix) {
        if (prefix.maskBits > key.maskBits)
            return false;
        
        if (prefix.maskBits == key.maskBits)
            return key.address == prefix.address;
        
        int mask = ~0 << (Integer.SIZE-prefix.maskBits);
        int km = mask & key.address;
        int pm = mask & prefix.address;
        return (km == pm);
    }

    protected static int lengthInBits(IPV4Subnet key) {
        return key.maskBits;
    }
}
