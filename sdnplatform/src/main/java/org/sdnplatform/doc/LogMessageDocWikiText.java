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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.MappingIterator;
import org.codehaus.jackson.map.ObjectMapper;

/**
 * Process the output of the LogMessageDocProcessor to produce a wiki-formatted
 * log message document.
 * @author readams
 */
public class LogMessageDocWikiText {

    public static void main(String[] args) 
                throws JsonParseException, JsonMappingException, IOException {
        String inputFile = 
                System.getProperty("org.sdnplatform.doc." + 
                                   "LogMessageDocWikiText.inputFile");
        String outputFile = 
                System.getProperty("org.sdnplatform.doc." + 
                                   "LogMessageDocWikiText.outputFile");
        
        InputStream is = new FileInputStream(inputFile);
        OutputStream os = new FileOutputStream(outputFile);
        PrintWriter out = new PrintWriter(os);
        
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory f = new JsonFactory();
        JsonParser jp = f.createJsonParser(is);
        MappingIterator<LogMessageDocItemData> mi =
                mapper.readValues(jp, LogMessageDocItemData.class);
        
        Map<String,List<LogMessageDocItemData>> lmdiMap =
                new HashMap<String,List<LogMessageDocItemData>>();
        
        while (mi.hasNext()) {
            LogMessageDocItemData lmdi = mi.nextValue();
            List<LogMessageDocItemData> dlist = lmdiMap.get(lmdi.getCategory());
            if (dlist == null)
                    lmdiMap.put(lmdi.getCategory(), 
                                dlist = new ArrayList<LogMessageDocItemData>());
            dlist.add(lmdi);
        }

        List<String> categories = new ArrayList<String>();
        categories.addAll(lmdiMap.keySet());
        Collections.sort(categories);
        for (String category : categories) {
            List<LogMessageDocItemData> dlist = lmdiMap.get(category);
            Collections.sort(dlist);
            out.println("h2. " + category);
            for (LogMessageDocItemData lmdi : dlist) {
                out.println("*Process Name*: sdnplatform");
                out.println("*Class Name*: _" + 
                            wikiEscape(lmdi.getClassName()) + "_");
                out.println("*Message*: {{" + wikiEscape(lmdi.getMessage()) +
                            "}}");
                out.println("*Severity*: " + wikiEscape(lmdi.getSeverity()));
                out.println("*Explanation*: " + wikiEscape(lmdi.getExplanation()));
                out.println("*Recommendation*: " + wikiEscape(lmdi.getRecommendation()));
                out.println("");
            }
            out.println("");
        }
        out.close();
        os.close();
    }
    
    protected static String wikiEscape(String str) {
        str = str.replaceAll("\\\\", "\\\\");
        str = str.replaceAll("\\[", "\\\\[");
        str = str.replaceAll("\\]", "\\\\]");
        str = str.replaceAll("\\{", "\\\\{");
        str = str.replaceAll("\\}", "\\\\}");
        // work around bug in confluence if the string ends with '}'
        if (str.endsWith("}"))
            return str + ".";
        return str;
    }

}
